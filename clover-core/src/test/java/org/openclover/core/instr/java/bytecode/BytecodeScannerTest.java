package org.openclover.core.instr.java.bytecode;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BytecodeScannerTest {

    private static final String COM_EXAMPLE_USER = "com/example/User";
    private static final String GET_NAME = "getName";
    private static final String SET_NAME = "setName";
    private static final String JAVA_LANG_OBJECT = "java/lang/Object";
    private static final String GET_NAME_METHOD_SPEC = "public:getName:()Ljava/lang/String;";
    private static final String SET_NAME_METHOD_SPEC = "public:setName:(Ljava/lang/String;)V";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private BytecodeScanner scanner;

    @Before
    public void setUp() {
        scanner = new BytecodeScanner();
    }

    @Test
    public void findGeneratedMethodsDetectsMethodNotInRegistry() throws IOException {
        // Create a .class file with methods getName() and setName()
        File classFile = createClassFile(COM_EXAMPLE_USER,
            GET_NAME_METHOD_SPEC,
            SET_NAME_METHOD_SPEC
        );

        // Registry only has getName() registered
        Map<String, Set<MethodSignatureKey>> phase1Methods = new HashMap<>();
        Set<MethodSignatureKey> methods = new HashSet<>();
        methods.add(new MethodSignatureKey(GET_NAME, 0));
        phase1Methods.put(COM_EXAMPLE_USER, methods);

        // Scanner should find setName() as generated
        List<GeneratedMethod> generated = scanner.findGeneratedMethods(
            tempDir.getRoot(),
            phase1Methods
        );

        assertEquals(1, generated.size());
        assertEquals(SET_NAME, generated.get(0).getMethodName());
        assertEquals("(Ljava/lang/String;)V", generated.get(0).getDescriptor());
    }

    @Test
    public void findGeneratedMethodsSkipsBridgeMethods() throws IOException {
        // Create a .class file with a bridge method
        File classFile = createClassFile(COM_EXAMPLE_USER,
            "bridge:getName:()Ljava/lang/Object;"
        );

        // Empty registry
        Map<String, Set<MethodSignatureKey>> phase1Methods = Collections.emptyMap();

        // Scanner should skip bridge method
        List<GeneratedMethod> generated = scanner.findGeneratedMethods(
            tempDir.getRoot(),
            phase1Methods
        );

        assertEquals(0, generated.size());
    }

    @Test
    public void findGeneratedMethodsSkipsSyntheticMethods() throws IOException {
        // Create a .class file with a synthetic method
        File classFile = createClassFile(COM_EXAMPLE_USER,
            "synthetic:access$000:()V"
        );

        // Empty registry
        Map<String, Set<MethodSignatureKey>> phase1Methods = Collections.emptyMap();

        // Scanner should skip synthetic method
        List<GeneratedMethod> generated = scanner.findGeneratedMethods(
            tempDir.getRoot(),
            phase1Methods
        );

        assertEquals(0, generated.size());
    }

    @Test
    public void findGeneratedMethodsHandlesEmptyDirectory() throws IOException {
        // Empty directory
        Map<String, Set<MethodSignatureKey>> phase1Methods = Collections.emptyMap();

        List<GeneratedMethod> generated = scanner.findGeneratedMethods(
            tempDir.getRoot(),
            phase1Methods
        );

        assertEquals(0, generated.size());
    }

    @Test
    public void findGeneratedMethodsHandlesClassNotInRegistry() throws IOException {
        // Create a .class file with methods
        File classFile = createClassFile(COM_EXAMPLE_USER,
            GET_NAME_METHOD_SPEC,
            SET_NAME_METHOD_SPEC
        );

        // Empty registry (class not in Phase 1)
        Map<String, Set<MethodSignatureKey>> phase1Methods = Collections.emptyMap();

        // All non-filtered methods should be reported as generated
        List<GeneratedMethod> generated = scanner.findGeneratedMethods(
            tempDir.getRoot(),
            phase1Methods
        );

        assertEquals(2, generated.size());
        assertTrue(generated.stream().anyMatch(m -> m.getMethodName().equals(GET_NAME)));
        assertTrue(generated.stream().anyMatch(m -> m.getMethodName().equals(SET_NAME)));
    }

    /**
     * Helper to create a .class file with specified methods.
     * @param className internal name like "com/example/User"
     * @param methodSpecs each is "access:name:descriptor" where access is "public", "bridge", "synthetic", etc.
     */
    private File createClassFile(String className, String... methodSpecs) throws IOException {
        ClassWriter cw = new ClassWriter(0);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, className, null, JAVA_LANG_OBJECT, null);

        for (String spec : methodSpecs) {
            String[] parts = spec.split(":");
            int access = parseAccess(parts[0]);
            String name = parts[1];
            String descriptor = parts[2];

            MethodVisitor mv = cw.visitMethod(access, name, descriptor, null, null);
            mv.visitCode();
            mv.visitInsn(Opcodes.RETURN);
            mv.visitMaxs(0, 0);
            mv.visitEnd();
        }

        cw.visitEnd();

        File classFile = new File(tempDir.getRoot(), className.replace('/', File.separatorChar) + ".class");
        classFile.getParentFile().mkdirs();
        Files.write(classFile.toPath(), cw.toByteArray());
        return classFile;
    }

    private int parseAccess(String accessStr) {
        switch (accessStr.toLowerCase()) {
            case "public":
                return Opcodes.ACC_PUBLIC;
            case "private":
                return Opcodes.ACC_PRIVATE;
            case "bridge":
                return Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE;
            case "synthetic":
                return Opcodes.ACC_PUBLIC | Opcodes.ACC_SYNTHETIC;
            case "abstract":
                return Opcodes.ACC_PUBLIC | Opcodes.ACC_ABSTRACT;
            case "native":
                return Opcodes.ACC_PUBLIC | Opcodes.ACC_NATIVE;
            default:
                return Opcodes.ACC_PUBLIC;
        }
    }
}
