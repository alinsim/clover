package org.openclover.core.instr.java.bytecode;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

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
    private static final String JAVA_LANG_OBJECT = "java/lang/Object";

    /**
     * Configuration needed for initializing recorder in generated-only classes.
     */
    public static class RecorderConfig {
        public final String dbPath;
        public final long dbVersion;
        public final long cfgBits;
        public final int maxDataIndex;
        public final File classDir;

        public RecorderConfig(String dbPath, long dbVersion, long cfgBits, int maxDataIndex) {
            this(dbPath, dbVersion, cfgBits, maxDataIndex, null);
        }

        public RecorderConfig(String dbPath, long dbVersion, long cfgBits, int maxDataIndex, File classDir) {
            this.dbPath = dbPath;
            this.dbVersion = dbVersion;
            this.cfgBits = cfgBits;
            this.maxDataIndex = maxDataIndex;
            this.classDir = classDir;
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
        // The default implementation uses Class.forName() which fails for project classes
        // not on the plugin's classloader. When that happens, resolve the hierarchy by
        // reading .class files from the project's classDir with ASM ClassReader.
        // Fall back to java/lang/Object only when classDir is unavailable or the class
        // file cannot be found.
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_FRAMES) {
            @Override
            protected String getCommonSuperClass(String type1, String type2) {
                try {
                    return super.getCommonSuperClass(type1, type2);
                } catch (RuntimeException e) {
                    try {
                        return resolveCommonSuperClass(type1, type2);
                    } catch (RuntimeException fallback) {
                        return JAVA_LANG_OBJECT;
                    }
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
     * Resolves the common superclass of two types by walking their superclass chains.
     * Uses Class.forName() for JDK/classpath types and ASM ClassReader for project
     * classes found in config.classDir. Falls back to java/lang/Object if resolution fails.
     */
    private String resolveCommonSuperClass(String type1, String type2) {
        if (JAVA_LANG_OBJECT.equals(type1) || JAVA_LANG_OBJECT.equals(type2)) {
            return JAVA_LANG_OBJECT;
        }

        if (config.classDir == null) {
            return JAVA_LANG_OBJECT;
        }

        Set<String> ancestors1 = collectSuperclassChain(type1);
        String current = type2;
        while (current != null) {
            if (ancestors1.contains(current)) {
                return current;
            }
            current = resolveSuperclass(current);
        }
        return JAVA_LANG_OBJECT;
    }

    /**
     * Collects the full superclass chain for a type, ending at java/lang/Object.
     */
    private Set<String> collectSuperclassChain(String type) {
        Set<String> chain = new LinkedHashSet<>();
        String current = type;
        while (current != null) {
            chain.add(current);
            if (JAVA_LANG_OBJECT.equals(current)) {
                break;
            }
            current = resolveSuperclass(current);
        }
        chain.add(JAVA_LANG_OBJECT);
        return chain;
    }

    /**
     * Resolves the direct superclass of a type. Tries Class.forName() first (for JDK
     * and classpath types), then falls back to reading the .class file from classDir.
     */
    private String resolveSuperclass(String type) {
        if (JAVA_LANG_OBJECT.equals(type)) {
            return null;
        }

        // Try classpath first (JDK classes, libraries already loaded)
        try {
            Class<?> cls = Class.forName(type.replace('/', '.'), false, getClass().getClassLoader());
            Class<?> superCls = cls.getSuperclass();
            return superCls != null ? superCls.getName().replace('.', '/') : null;
        } catch (ClassNotFoundException ignored) {
            // Fall through to classDir resolution
        }

        // Try reading .class file from project classDir
        if (config.classDir != null) {
            File classFile = new File(config.classDir, type + ".class");
            if (classFile.isFile()) {
                try {
                    byte[] bytes = Files.readAllBytes(classFile.toPath());
                    return new ClassReader(bytes).getSuperName();
                } catch (Exception ignored) {
                    // IO error or corrupt class file — fall through to null
                }
            }
        }

        return null;
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
