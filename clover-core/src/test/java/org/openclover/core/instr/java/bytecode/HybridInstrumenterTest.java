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

/**
 * Tests the HybridInstrumenter pipeline using a pre-built phase1Methods map
 * (bypassing the registry, which requires complex setup).
 */
public class HybridInstrumenterTest {

    private static final String CLASS_NAME = "com/example/User";
    private static final String DESC_STRING = "()Ljava/lang/String;";
    private static final String DESC_STRING_PARAM = "(Ljava/lang/String;)V";
    private static final String CLASS_FILE_SUFFIX = ".class";
    private static final String SUPER_CLASS = "java/lang/Object";
    private static final String METHOD_GET_DISPLAY_NAME = "getDisplayName";
    private static final String METHOD_GET_NAME = "getName";
    private static final String METHOD_SET_NAME = "setName";

    @Rule
    public TemporaryFolder tempDir = new TemporaryFolder();

    private File classDir;

    @Before
    public void setUp() throws IOException {
        classDir = tempDir.newFolder("classes");
    }

    @Test
    public void scanAndInstrumentFindsGeneratedMethods() throws IOException {
        createClassFile(CLASS_NAME, new String[][] {
            {METHOD_GET_DISPLAY_NAME, DESC_STRING, String.valueOf(Opcodes.ACC_PUBLIC)},
            {METHOD_GET_NAME, DESC_STRING, String.valueOf(Opcodes.ACC_PUBLIC)},
            {METHOD_SET_NAME, DESC_STRING_PARAM, String.valueOf(Opcodes.ACC_PUBLIC)}
        });

        Map<String, Set<MethodSignatureKey>> phase1Methods = new HashMap<>();
        Set<MethodSignatureKey> userMethods = new HashSet<>();
        userMethods.add(new MethodSignatureKey(METHOD_GET_DISPLAY_NAME, 0));
        phase1Methods.put(CLASS_NAME, userMethods);

        BytecodeScanner scanner = new BytecodeScanner();
        List<GeneratedMethod> generated = scanner.findGeneratedMethods(classDir, phase1Methods);

        assertEquals(2, generated.size());
    }

    @Test
    public void fullPipelineScanAllocateInstrument() throws IOException {
        createClassFile(CLASS_NAME, new String[][] {
            {METHOD_GET_NAME, DESC_STRING, String.valueOf(Opcodes.ACC_PUBLIC)},
            {METHOD_SET_NAME, DESC_STRING_PARAM, String.valueOf(Opcodes.ACC_PUBLIC)}
        });

        // Class must be in phase1Methods (even with empty set) to be recognized as a project class
        Map<String, Set<MethodSignatureKey>> phase1Methods = new HashMap<>();
        phase1Methods.put(CLASS_NAME, new HashSet<>());

        BytecodeScanner scanner = new BytecodeScanner();
        List<GeneratedMethod> generated = scanner.findGeneratedMethods(classDir, phase1Methods);
        assertEquals(2, generated.size());

        Phase2IndexAllocator allocator = new Phase2IndexAllocator(500);
        Map<GeneratedMethod, Integer> indexMap = allocator.allocateIndices(generated);
        assertEquals(502, allocator.getTotalMaxIndex());

        BytecodeInstrumenter.RecorderConfig config = new BytecodeInstrumenter.RecorderConfig(
                "/tmp/test.db", 1L, 0L, 502);
        BytecodeInstrumenter instrumenter = new BytecodeInstrumenter(config);

        Map<String, Integer> methodIndices = new HashMap<>();
        for (Map.Entry<GeneratedMethod, Integer> entry : indexMap.entrySet()) {
            GeneratedMethod m = entry.getKey();
            methodIndices.put(m.getMethodName() + m.getDescriptor(), entry.getValue());
        }

        File classFile = new File(classDir, CLASS_NAME + CLASS_FILE_SUFFIX);
        byte[] original = Files.readAllBytes(classFile.toPath());
        byte[] instrumented = instrumenter.instrument(original, methodIndices);

        assertTrue("Instrumented bytes should be larger", instrumented.length > original.length);

        Files.write(classFile.toPath(), instrumented);
        assertTrue(classFile.length() > 0);
    }

    @Test
    public void pipelineSkipsBridgeAndSyntheticMethods() throws IOException {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cw.visit(Opcodes.V17, Opcodes.ACC_PUBLIC, CLASS_NAME, null, SUPER_CLASS, null);

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, METHOD_GET_NAME, DESC_STRING, null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_PUBLIC | Opcodes.ACC_BRIDGE | Opcodes.ACC_SYNTHETIC,
                "get", "()Ljava/lang/Object;", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ARETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        mv = cw.visitMethod(Opcodes.ACC_STATIC | Opcodes.ACC_SYNTHETIC,
                "access$000", "()I", null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.ICONST_0);
        mv.visitInsn(Opcodes.IRETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();
        writeClassFile(CLASS_NAME, cw.toByteArray());

        // Class must be in phase1Methods to be recognized as a project class
        Map<String, Set<MethodSignatureKey>> phase1Methods = new HashMap<>();
        phase1Methods.put(CLASS_NAME, new HashSet<>());

        BytecodeScanner scanner = new BytecodeScanner();
        List<GeneratedMethod> generated = scanner.findGeneratedMethods(classDir, phase1Methods);

        // Only getName found (bridge and synthetic filtered)
        assertEquals(1, generated.size());
        assertEquals(METHOD_GET_NAME, generated.get(0).getMethodName());
    }

    @Test
    public void pipelineHandlesEmptyClassDirectory() throws IOException {
        Map<String, Set<MethodSignatureKey>> phase1Methods = Collections.emptyMap();
        BytecodeScanner scanner = new BytecodeScanner();
        List<GeneratedMethod> generated = scanner.findGeneratedMethods(classDir, phase1Methods);

        assertTrue(generated.isEmpty());
    }

    private void createClassFile(String className, String[][] methods) throws IOException {
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
            } else {
                mv.visitInsn(Opcodes.ACONST_NULL);
                mv.visitInsn(Opcodes.ARETURN);
            }
            mv.visitMaxs(1, 1);
            mv.visitEnd();
        }

        cw.visitEnd();
        writeClassFile(className, cw.toByteArray());
    }

    private void writeClassFile(String className, byte[] bytes) throws IOException {
        File classFile = new File(classDir, className + CLASS_FILE_SUFFIX);
        classFile.getParentFile().mkdirs();
        Files.write(classFile.toPath(), bytes);
    }
}
