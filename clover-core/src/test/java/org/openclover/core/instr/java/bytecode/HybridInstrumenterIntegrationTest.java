package org.openclover.core.instr.java.bytecode;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.openclover.core.api.instrumentation.ConcurrentInstrumentationException;
import org.openclover.core.api.instrumentation.InstrumentationSession;
import org.openclover.core.api.registry.ClassInfo;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.api.registry.MethodInfo;
import org.openclover.core.api.registry.PackageInfo;
import org.openclover.core.context.ContextSetImpl;
import org.openclover.core.instr.InstrumentationSessionImpl;
import org.openclover.core.registry.Clover2Registry;
import org.openclover.core.registry.FixedSourceRegion;
import org.openclover.core.registry.entities.MethodSignature;
import org.openclover.core.registry.entities.Modifiers;
import org.openclover.runtime.api.CloverException;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Integration test for hybrid instrumentation pipeline simulating real Lombok scenarios.
 * Tests Phase 1 (source instrumentation) → Phase 2 (bytecode scanning and instrumentation).
 */
public class HybridInstrumenterIntegrationTest {

    private static final String PACKAGE_NAME = "com.example";
    private static final String CLASS_NAME = "User";
    private static final String CLASS_INTERNAL_NAME = "com/example/User";
    private static final String BUILDER_INTERNAL_NAME = "com/example/User$UserBuilder";
    private static final String SOURCE_FILE = "com/example/User.java";
    private static final String CLASS_FILE_EXTENSION = ".class";
    private static final String SUPER_CLASS = "java/lang/Object";
    private static final String RECORDER_FIELD_NAME = "__CLR$R";

    private static final String METHOD_GET_DISPLAY_NAME = "getDisplayName";
    private static final String METHOD_GET_NAME = "getName";
    private static final String METHOD_SET_NAME = "setName";
    private static final String METHOD_EQUALS = "equals";
    private static final String METHOD_HASH_CODE = "hashCode";
    private static final String METHOD_TO_STRING = "toString";
    private static final String METHOD_NAME_BUILDER = "name";
    private static final String METHOD_BUILD = "build";

    private static final String DESC_STRING = "()Ljava/lang/String;";
    private static final String DESC_INT = "()I";
    private static final String DESC_VOID = "()V";
    private static final String DESC_SET_STRING = "(Ljava/lang/String;)V";
    private static final String DESC_EQUALS = "(Ljava/lang/Object;)Z";
    private static final String DESC_BUILDER_NAME = "(Ljava/lang/String;)Lcom/example/User$UserBuilder;";
    private static final String DESC_BUILDER_BUILD = "()Lcom/example/User;";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private File registryDir;
    private File classDir;
    private File registryFile;

    @Before
    public void setUp() throws IOException {
        registryDir = tempDir.newFolder("registry");
        classDir = tempDir.newFolder("classes");
        registryFile = new File(registryDir, "clover.db");
    }

    @Test
    public void fullPipelineWithRealRegistrySimulatingLombokDataClass()
            throws IOException, CloverException, ConcurrentInstrumentationException {

        // Phase 1: Source instrumentation simulation
        Clover2Registry registry = new Clover2Registry(registryFile, "test");
        InstrumentationSession session = registry.startInstr();

        long timestamp = System.currentTimeMillis();
        long filesize = 500L;
        long checksum = 123456789L;

        session.enterFile(PACKAGE_NAME, new File(SOURCE_FILE), 20, 15, timestamp, filesize, checksum);
        session.enterClass(CLASS_NAME, new FixedSourceRegion(1, 0), new Modifiers(), false, false, false);

        MethodInfo handWrittenMethod = ((InstrumentationSessionImpl) session).enterMethod(
            new ContextSetImpl(),
            new FixedSourceRegion(10, 0),
            new MethodSignature(METHOD_GET_DISPLAY_NAME),
            false
        );

        session.exitMethod(15, 0);
        session.exitClass(20, 0);
        session.exitFile();
        session.close();

        registry.saveAndOverwriteFile();

        int phase1DataIndex = handWrittenMethod.getDataIndex();
        int phase1DataLength = registry.getProject().getDataLength();

        // Phase 2: Generate Lombok-like .class file with generated methods
        createUserClassFile(CLASS_INTERNAL_NAME, new String[][] {
            {METHOD_GET_DISPLAY_NAME, DESC_STRING, String.valueOf(Opcodes.ACC_PUBLIC)},
            {METHOD_GET_NAME, DESC_STRING, String.valueOf(Opcodes.ACC_PUBLIC)},
            {METHOD_SET_NAME, DESC_SET_STRING, String.valueOf(Opcodes.ACC_PUBLIC)},
            {METHOD_EQUALS, DESC_EQUALS, String.valueOf(Opcodes.ACC_PUBLIC)},
            {METHOD_HASH_CODE, DESC_INT, String.valueOf(Opcodes.ACC_PUBLIC)},
            {METHOD_TO_STRING, DESC_STRING, String.valueOf(Opcodes.ACC_PUBLIC)}
        });

        File userClassFile = new File(classDir, CLASS_INTERNAL_NAME + CLASS_FILE_EXTENSION);
        long originalSize = userClassFile.length();
        byte[] originalBytes = Files.readAllBytes(userClassFile.toPath());

        // Phase 2: Run hybrid instrumentation
        HybridInstrumenter instrumenter = new HybridInstrumenter();
        HybridInstrumenter.Result result = instrumenter.instrument(classDir, registryFile, 0L, 0L);

        // Assertions
        assertEquals("Expected 5 generated methods (all except getDisplayName)",
                     5, result.getGeneratedMethodsFound());
        assertEquals("Expected 1 class file modified",
                     1, result.getClassFilesModified());
        assertEquals("Total max data index should be phase1 + 5 generated methods",
                     phase1DataLength + 5, result.getTotalMaxDataIndex());

        // Verify class file was modified
        byte[] instrumentedBytes = Files.readAllBytes(userClassFile.toPath());
        assertTrue("Instrumented class file should be larger than original",
                   instrumentedBytes.length > originalSize);

        // Verify getName() method was instrumented with inc() call
        assertTrue("getName() should have inc() call",
                   methodHasIncCall(instrumentedBytes, METHOD_GET_NAME, DESC_STRING));

        // Verify recorder field was added
        assertTrue("Class should have recorder field",
                   classHasField(instrumentedBytes, RECORDER_FIELD_NAME));
    }

    @Test
    public void fullPipelineWithGeneratedOnlyInnerClass()
            throws IOException, CloverException, ConcurrentInstrumentationException {

        // Phase 1: Register only User class with getDisplayName
        Clover2Registry registry = new Clover2Registry(registryFile, "test");
        InstrumentationSession session = registry.startInstr();

        long timestamp = System.currentTimeMillis();

        session.enterFile(PACKAGE_NAME, new File(SOURCE_FILE), 20, 15, timestamp, 500L, 123456789L);
        session.enterClass(CLASS_NAME, new FixedSourceRegion(1, 0), new Modifiers(), false, false, false);

        ((InstrumentationSessionImpl) session).enterMethod(
            new ContextSetImpl(),
            new FixedSourceRegion(10, 0),
            new MethodSignature(METHOD_GET_DISPLAY_NAME),
            false
        );

        session.exitMethod(15, 0);
        session.exitClass(20, 0);
        session.exitFile();
        session.close();

        registry.saveAndOverwriteFile();
        int phase1DataLength = registry.getProject().getDataLength();

        // Phase 2: Generate User.class with getDisplayName
        createUserClassFile(CLASS_INTERNAL_NAME, new String[][] {
            {METHOD_GET_DISPLAY_NAME, DESC_STRING, String.valueOf(Opcodes.ACC_PUBLIC)}
        });

        // Phase 2: Generate User$UserBuilder.class with builder methods (not in Phase 1)
        createUserClassFile(BUILDER_INTERNAL_NAME, new String[][] {
            {METHOD_NAME_BUILDER, DESC_BUILDER_NAME, String.valueOf(Opcodes.ACC_PUBLIC)},
            {METHOD_BUILD, DESC_BUILDER_BUILD, String.valueOf(Opcodes.ACC_PUBLIC)}
        });

        File builderClassFile = new File(classDir, BUILDER_INTERNAL_NAME + CLASS_FILE_EXTENSION);
        byte[] originalBuilderBytes = Files.readAllBytes(builderClassFile.toPath());

        // Phase 2: Run hybrid instrumentation
        HybridInstrumenter instrumenter = new HybridInstrumenter();
        HybridInstrumenter.Result result = instrumenter.instrument(classDir, registryFile, 0L, 0L);

        // Assertions
        assertEquals("Expected 2 generated methods from UserBuilder",
                     2, result.getGeneratedMethodsFound());
        assertEquals("Expected 1 class file modified (UserBuilder only)",
                     1, result.getClassFilesModified());
        assertEquals("Total max data index should include builder methods",
                     phase1DataLength + 2, result.getTotalMaxDataIndex());

        // Verify UserBuilder.class was instrumented
        byte[] instrumentedBuilderBytes = Files.readAllBytes(builderClassFile.toPath());
        assertTrue("UserBuilder class should be larger after instrumentation",
                   instrumentedBuilderBytes.length > originalBuilderBytes.length);

        // Verify builder methods were instrumented
        assertTrue("name() builder method should have inc() call",
                   methodHasIncCall(instrumentedBuilderBytes, METHOD_NAME_BUILDER, DESC_BUILDER_NAME));
        assertTrue("build() method should have inc() call",
                   methodHasIncCall(instrumentedBuilderBytes, METHOD_BUILD, DESC_BUILDER_BUILD));

        // Verify recorder field was added to UserBuilder
        assertTrue("UserBuilder should have recorder field",
                   classHasField(instrumentedBuilderBytes, RECORDER_FIELD_NAME));
    }

    @Test
    public void pipelineSkipsAlreadyInstrumentedMethods()
            throws IOException, CloverException, ConcurrentInstrumentationException {

        // Phase 1: Register ALL methods
        Clover2Registry registry = new Clover2Registry(registryFile, "test");
        InstrumentationSession session = registry.startInstr();

        long timestamp = System.currentTimeMillis();

        session.enterFile(PACKAGE_NAME, new File(SOURCE_FILE), 30, 20, timestamp, 500L, 123456789L);
        session.enterClass(CLASS_NAME, new FixedSourceRegion(1, 0), new Modifiers(), false, false, false);

        // Register both methods in Phase 1
        ((InstrumentationSessionImpl) session).enterMethod(
            new ContextSetImpl(),
            new FixedSourceRegion(10, 0),
            new MethodSignature(METHOD_GET_DISPLAY_NAME),
            false
        );
        session.exitMethod(15, 0);

        ((InstrumentationSessionImpl) session).enterMethod(
            new ContextSetImpl(),
            new FixedSourceRegion(20, 0),
            new MethodSignature(METHOD_GET_NAME),
            false
        );
        session.exitMethod(25, 0);

        session.exitClass(30, 0);
        session.exitFile();
        session.close();

        registry.saveAndOverwriteFile();
        int phase1DataLength = registry.getProject().getDataLength();

        // Phase 2: Generate .class with same methods
        createUserClassFile(CLASS_INTERNAL_NAME, new String[][] {
            {METHOD_GET_DISPLAY_NAME, DESC_STRING, String.valueOf(Opcodes.ACC_PUBLIC)},
            {METHOD_GET_NAME, DESC_STRING, String.valueOf(Opcodes.ACC_PUBLIC)}
        });

        File userClassFile = new File(classDir, CLASS_INTERNAL_NAME + CLASS_FILE_EXTENSION);
        long originalSize = userClassFile.length();

        // Phase 2: Run hybrid instrumentation
        HybridInstrumenter instrumenter = new HybridInstrumenter();
        HybridInstrumenter.Result result = instrumenter.instrument(classDir, registryFile, 0L, 0L);

        // Assertions: No generated methods found, no files modified
        assertEquals("Expected 0 generated methods (all in Phase 1)",
                     0, result.getGeneratedMethodsFound());
        assertEquals("Expected 0 class files modified",
                     0, result.getClassFilesModified());
        assertEquals("Total max data index should remain unchanged",
                     phase1DataLength, result.getTotalMaxDataIndex());

        // Verify class file was NOT modified
        long finalSize = userClassFile.length();
        assertEquals("Class file size should not change", originalSize, finalSize);
    }

    @Test
    public void generatedMethodsRegisteredInRegistryForReportVisibility()
            throws IOException, CloverException, ConcurrentInstrumentationException {

        // Phase 1: Register only getDisplayName
        Clover2Registry registry = new Clover2Registry(registryFile, "test");
        InstrumentationSession session = registry.startInstr();

        session.enterFile(PACKAGE_NAME, new File(SOURCE_FILE), 20, 15,
                System.currentTimeMillis(), 500L, 111111L);
        session.enterClass(CLASS_NAME, new FixedSourceRegion(1, 0), new Modifiers(), false, false, false);

        ((InstrumentationSessionImpl) session).enterMethod(
            new ContextSetImpl(), new FixedSourceRegion(10, 0),
            new MethodSignature(METHOD_GET_DISPLAY_NAME), false);
        session.exitMethod(15, 0);
        session.exitClass(20, 0);
        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        // Phase 2: .class file has 3 methods (1 Phase 1 + 2 generated)
        createUserClassFile(CLASS_INTERNAL_NAME, new String[][] {
            {METHOD_GET_DISPLAY_NAME, DESC_STRING, String.valueOf(Opcodes.ACC_PUBLIC)},
            {METHOD_GET_NAME, DESC_STRING, String.valueOf(Opcodes.ACC_PUBLIC)},
            {METHOD_SET_NAME, DESC_SET_STRING, String.valueOf(Opcodes.ACC_PUBLIC)}
        });

        HybridInstrumenter instrumenter = new HybridInstrumenter();
        HybridInstrumenter.Result result = instrumenter.instrument(classDir, registryFile, 0L, 0L);
        assertEquals(2, result.getGeneratedMethodsFound());

        // RELOAD registry and verify Phase 2 methods are registered
        Clover2Registry reloaded = Clover2Registry.fromFile(registryFile);
        int totalMethods = 0;
        boolean foundGetName = false;
        boolean foundSetName = false;

        for (PackageInfo pkg : reloaded.getProject().getAllPackages()) {
            for (FileInfo file : pkg.getFiles()) {
                for (ClassInfo clazz : file.getClasses()) {
                    for (MethodInfo method : clazz.getMethods()) {
                        totalMethods++;
                        if (method.getSimpleName().equals(METHOD_GET_NAME)) {
                            foundGetName = true;
                        }
                        if (method.getSimpleName().equals(METHOD_SET_NAME)) {
                            foundSetName = true;
                        }
                    }
                }
            }
        }

        // With rollup implementation: generated methods ARE added to parent class.
        // Phase 1: getDisplayName (1 method)
        // Phase 2: getName, setName (2 generated methods rolled up to User class)
        // Total: 3 methods in User class
        assertEquals("Registry should have 3 methods (1 Phase 1 + 2 rolled up)", 3, totalMethods);
        assertTrue("getName should be found in registry", foundGetName);
        assertTrue("setName should be found in registry", foundSetName);

        // Verify NO duplicate class names
        int userClassCount = 0;
        for (PackageInfo pkg : reloaded.getProject().getAllPackages()) {
            for (FileInfo file : pkg.getFiles()) {
                for (ClassInfo clazz : file.getClasses()) {
                    if (CLASS_NAME.equals(clazz.getName())) {
                        userClassCount++;
                    }
                }
            }
        }
        assertEquals("Class should appear only once (no duplicate from Phase 2)", 1, userClassCount);
    }

    // Helper methods

    private void createUserClassFile(String className, String[][] methods) throws IOException {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, SUPER_CLASS, null);

        for (String[] method : methods) {
            String name = method[0];
            String desc = method[1];
            int access = Integer.parseInt(method[2]);

            MethodVisitor mv = cw.visitMethod(access, name, desc, null, null);
            mv.visitCode();

            if (desc.endsWith("V")) {
                mv.visitInsn(Opcodes.RETURN);
                mv.visitMaxs(0, 1);
            } else if (desc.endsWith("I")) {
                mv.visitInsn(Opcodes.ICONST_0);
                mv.visitInsn(Opcodes.IRETURN);
                mv.visitMaxs(1, 1);
            } else if (desc.endsWith("Z")) {
                mv.visitInsn(Opcodes.ICONST_1);
                mv.visitInsn(Opcodes.IRETURN);
                mv.visitMaxs(1, 1);
            } else {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
                mv.visitMaxs(1, 1);
            }

            mv.visitEnd();
        }

        cw.visitEnd();
        writeClassFile(className, cw.toByteArray());
    }

    private void writeClassFile(String className, byte[] bytes) throws IOException {
        File classFile = new File(classDir, className + CLASS_FILE_EXTENSION);
        classFile.getParentFile().mkdirs();
        Files.write(classFile.toPath(), bytes);
    }

    private boolean methodHasIncCall(byte[] classBytes, String methodName, String descriptor) {
        boolean[] found = {false};
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exc) {
                if (name.equals(methodName) && desc.equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitMethodInsn(int opcode, String owner, String mName, String mDesc, boolean itf) {
                            if ("inc".equals(mName) && "(I)V".equals(mDesc)) {
                                found[0] = true;
                            }
                        }
                    };
                }
                return null;
            }
        }, 0);
        return found[0];
    }

    private boolean classHasField(byte[] classBytes, String fieldName) {
        boolean[] found = {false};
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String desc, String sig, Object value) {
                if (fieldName.equals(name)) {
                    found[0] = true;
                }
                return null;
            }
        }, 0);
        return found[0];
    }
}
