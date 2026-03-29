package org.openclover.core.instr.java.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.Map;

public class BytecodeInstrumenter {

    private static final String COVERAGE_RECORDER_TYPE = "org_openclover_runtime/CoverageRecorder";
    private static final String COVERAGE_RECORDER_DESC = "Lorg_openclover_runtime/CoverageRecorder;";
    private static final String CLR_PREFIX = "__CLR";
    private static final String RECORDER_FIELD_NAME = "R";
    private static final String PHASE2_RECORDER_FIELD = "__CLR$R";
    private static final String INC_METHOD_NAME = "inc";
    private static final String INC_METHOD_DESC = "(I)V";
    private static final String CLOVER_CLASS = "org_openclover_runtime/Clover";
    private static final String GET_RECORDER_METHOD = "getRecorder";
    private static final String GET_RECORDER_DESC = "(Ljava/lang/String;JJI[Lorg_openclover_runtime/CloverProfile;[Ljava/lang/String;)Lorg_openclover_runtime/CoverageRecorder;";
    private static final String CLINIT_METHOD_NAME = "<clinit>";
    private static final String CLINIT_METHOD_DESC = "()V";

    /**
     * Configuration needed for initializing recorder in generated-only classes.
     */
    public static class RecorderConfig {
        public final String dbPath;
        public final long dbVersion;
        public final long cfgBits;
        public final int maxDataIndex;

        public RecorderConfig(String dbPath, long dbVersion, long cfgBits, int maxDataIndex) {
            this.dbPath = dbPath;
            this.dbVersion = dbVersion;
            this.cfgBits = cfgBits;
            this.maxDataIndex = maxDataIndex;
        }
    }

    private final RecorderConfig config;

    public BytecodeInstrumenter(RecorderConfig config) {
        this.config = config;
    }

    /**
     * Instruments a single .class file, injecting R.inc(N) at method entry
     * for the specified generated methods.
     *
     * @param classFileBytes original .class file bytes
     * @param methodIndices  map of method name+descriptor to data index
     * @return modified .class file bytes, or null if no changes needed
     */
    public byte[] instrument(byte[] classFileBytes, Map<String, Integer> methodIndices) {
        if (methodIndices == null || methodIndices.isEmpty()) {
            return null;
        }

        ClassReader reader = new ClassReader(classFileBytes);

        // Skip interfaces — adding public static non-final fields is illegal in interfaces
        // and causes ClassFormatError (e.g., "Illegal field modifiers in class: 0x9")
        // Skip enums — injecting recorder init into <clinit> runs before enum constants
        // are initialized, causing NoClassDefFoundError on first access
        int classAccess = reader.getAccess();
        if ((classAccess & Opcodes.ACC_INTERFACE) != 0 || (classAccess & Opcodes.ACC_ENUM) != 0) {
            return null;
        }

        // First pass: detect if class has __CLR inner class (Case 1) or not (Case 2)
        String recorderOwner = findRecorderOwner(reader);

        // Second pass: transform
        // COMPUTE_FRAMES requires class hierarchy resolution via getCommonSuperClass().
        // The default implementation uses Class.forName() which fails when target classes
        // (Vert.x, SLF4J, etc.) aren't on the plugin's classloader. Override to fall back
        // to java/lang/Object — conservative but always valid for the JVM verifier.
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (RuntimeException e) {
                    return "java/lang/Object";
                }
            }
        };

        String[] classNameHolder = new String[1];
        boolean[] hasClinitHolder = new boolean[1];

        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public void visit(int version, int access, String name, String signature, String superName, String[] interfaces) {
                classNameHolder[0] = name;
                super.visit(version, access, name, signature, superName, interfaces);
            }

            @Override
            public MethodVisitor visitMethod(int access, String name, String descriptor, String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, name, descriptor, signature, exceptions);
                String key = name + descriptor;
                Integer index = methodIndices.get(key);

                // Handle clinit for Case 2 (generated-only class)
                if (CLINIT_METHOD_NAME.equals(name) && CLINIT_METHOD_DESC.equals(descriptor)) {
                    hasClinitHolder[0] = true;
                    if (recorderOwner == null) {
                        // Existing clinit - inject recorder initialization at the start
                        return new MethodVisitor(Opcodes.ASM9, mv) {
                            @Override
                            public void visitCode() {
                                super.visitCode();
                                injectRecorderInitialization(mv, classNameHolder[0]);
                            }
                        };
                    }
                }

                if (index != null) {
                    return new MethodVisitor(Opcodes.ASM9, mv) {
                        @Override
                        public void visitCode() {
                            super.visitCode();

                            // Inject: recorderOwner.R.inc(index)
                            String fieldOwner = recorderOwner != null ? recorderOwner : classNameHolder[0];
                            String fieldName = recorderOwner != null ? RECORDER_FIELD_NAME : PHASE2_RECORDER_FIELD;

                            mv.visitFieldInsn(Opcodes.GETSTATIC, fieldOwner, fieldName, COVERAGE_RECORDER_DESC);
                            mv.visitLdcInsn(index);
                            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, COVERAGE_RECORDER_TYPE, INC_METHOD_NAME, INC_METHOD_DESC, false);
                        }
                    };
                }

                return mv;
            }

            @Override
            public void visitEnd() {
                // Case 2: Add __CLR$R field and clinit if no recorder owner exists
                if (recorderOwner == null) {
                    cv.visitField(Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
                                  PHASE2_RECORDER_FIELD,
                                  COVERAGE_RECORDER_DESC,
                                  null,
                                  null).visitEnd();

                    // Create clinit if it doesn't exist
                    if (!hasClinitHolder[0]) {
                        MethodVisitor clinitMv = cv.visitMethod(Opcodes.ACC_STATIC,
                                                                 CLINIT_METHOD_NAME,
                                                                 CLINIT_METHOD_DESC,
                                                                 null,
                                                                 null);
                        clinitMv.visitCode();
                        injectRecorderInitialization(clinitMv, classNameHolder[0]);
                        clinitMv.visitInsn(Opcodes.RETURN);
                        clinitMv.visitMaxs(0, 0);
                        clinitMv.visitEnd();
                    }
                }
                super.visitEnd();
            }
        }, 0);

        return writer.toByteArray();
    }

    /**
     * Injects recorder initialization bytecode for Case 2 (generated-only classes).
     */
    private void injectRecorderInitialization(MethodVisitor mv, String className) {
        // LDC <dbPath>
        mv.visitLdcInsn(config.dbPath);

        // LDC2_W <dbVersion>
        mv.visitLdcInsn(config.dbVersion);

        // LDC2_W <cfgBits>
        mv.visitLdcInsn(config.cfgBits);

        // LDC <maxDataIndex>
        mv.visitLdcInsn(config.maxDataIndex);

        // ACONST_NULL (profiles)
        mv.visitInsn(Opcodes.ACONST_NULL);

        // ACONST_NULL (properties)
        mv.visitInsn(Opcodes.ACONST_NULL);

        // INVOKESTATIC Clover.getRecorder
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                          CLOVER_CLASS,
                          GET_RECORDER_METHOD,
                          GET_RECORDER_DESC,
                          false);

        // PUTSTATIC className.__CLR$R
        mv.visitFieldInsn(Opcodes.PUTSTATIC, className, PHASE2_RECORDER_FIELD, COVERAGE_RECORDER_DESC);
    }

    /**
     * Finds the __CLR inner class that contains the recorder field.
     * Returns null if no such inner class exists (Case 2: generated-only class).
     */
    private String findRecorderOwner(ClassReader reader) {
        String[] result = new String[1];

        reader.accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public void visitInnerClass(String name, String outerName, String innerName, int access) {
                if (innerName != null && innerName.startsWith(CLR_PREFIX)) {
                    result[0] = name;
                }
            }
        }, 0);

        return result[0];
    }
}
