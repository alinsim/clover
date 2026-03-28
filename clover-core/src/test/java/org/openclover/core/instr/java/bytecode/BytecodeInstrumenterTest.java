package org.openclover.core.instr.java.bytecode;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.FieldVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class BytecodeInstrumenterTest {

    private static final String COVERAGE_RECORDER_TYPE = "org_openclover_runtime/CoverageRecorder";
    private static final String COVERAGE_RECORDER_DESC = "Lorg_openclover_runtime/CoverageRecorder;";
    private static final String TEST_CLASS_NAME = "com/example/TestClass";
    private static final String RECORDER_OWNER = "com/example/TestClass$__CLR4_5_0_test";
    private static final String RECORDER_INNER_NAME = "__CLR4_5_0_test";
    private static final String RECORDER_FIELD_NAME = "R";
    private static final String PHASE2_RECORDER_FIELD = "__CLR$R";
    private static final String METHOD_NAME_GET_NAME = "getName";
    private static final String METHOD_DESC_VOID = "()V";
    private static final String METHOD_NAME_CLINIT = "<clinit>";
    private static final String OPCODE_GETSTATIC = "GETSTATIC";
    private static final String OPCODE_INVOKEVIRTUAL = "INVOKEVIRTUAL";
    private static final String OPCODE_INVOKESTATIC = "INVOKESTATIC";
    private static final String JAVA_LANG_OBJECT = "java/lang/Object";
    private static final String TEST_DB_PATH = "/tmp/test.db";
    private static final String MSG_INSTRUMENTED_NOT_NULL = "Instrumented bytes should not be null";
    private static final String MSG_SHOULD_HAVE_INC = "Should have INVOKEVIRTUAL inc";
    private static final String METHOD_NAME_INC = "inc";
    private static final String METHOD_NAME_A = "methodA";
    private static final String METHOD_NAME_B = "methodB";
    private static final String METHOD_NAME_C = "methodC";
    private static final String METHOD_KEY_A = "methodA()V";
    private static final String METHOD_KEY_B = "methodB()V";
    private static final String METHOD_KEY_C = "methodC()V";
    private static final String LDC_100 = "LDC 100";
    private static final String LDC_200 = "LDC 200";
    private static final String LDC_300 = "LDC 300";

    @Test
    public void instrumentInjectsIncCallForSameClassWithRecorder() {
        // Arrange: Generate a class with __CLR inner class and recorder field
        byte[] classBytes = generateClassWithRecorder();

        Map<String, Integer> methodIndices = new HashMap<>();
        methodIndices.put(METHOD_NAME_GET_NAME + METHOD_DESC_VOID, 42);

        BytecodeInstrumenter.RecorderConfig config = new BytecodeInstrumenter.RecorderConfig(
            TEST_DB_PATH, 1234L, 5678L, 100
        );
        BytecodeInstrumenter instrumenter = new BytecodeInstrumenter(config);

        // Act
        byte[] result = instrumenter.instrument(classBytes, methodIndices);

        // Assert
        assertNotNull(MSG_INSTRUMENTED_NOT_NULL, result);

        // Verify the bytecode contains GETSTATIC + LDC 42 + INVOKEVIRTUAL
        List<String> instructions = getMethodInstructions(result, METHOD_NAME_GET_NAME, METHOD_DESC_VOID);

        assertTrue("Should have GETSTATIC for recorder",
            instructions.stream().anyMatch(s -> s.contains(OPCODE_GETSTATIC) && s.contains(RECORDER_OWNER) && s.contains(RECORDER_FIELD_NAME)));
        assertTrue("Should have LDC 42",
            instructions.stream().anyMatch(s -> s.equals("LDC 42")));
        assertTrue(MSG_SHOULD_HAVE_INC,
            instructions.stream().anyMatch(s -> s.contains(OPCODE_INVOKEVIRTUAL) && s.contains(COVERAGE_RECORDER_TYPE) && s.contains(METHOD_NAME_INC)));
    }

    @Test
    public void instrumentInjectsIncCallForGeneratedOnlyClass() {
        // Arrange: Generate a class WITHOUT __CLR inner class
        byte[] classBytes = generateClassWithoutRecorder();

        Map<String, Integer> methodIndices = new HashMap<>();
        methodIndices.put(METHOD_NAME_GET_NAME + METHOD_DESC_VOID, 100);

        BytecodeInstrumenter.RecorderConfig config = new BytecodeInstrumenter.RecorderConfig(
            TEST_DB_PATH, 1234L, 5678L, 200
        );
        BytecodeInstrumenter instrumenter = new BytecodeInstrumenter(config);

        // Act
        byte[] result = instrumenter.instrument(classBytes, methodIndices);

        // Assert
        assertNotNull(MSG_INSTRUMENTED_NOT_NULL, result);

        // Verify the class has __CLR$R field added
        assertTrue("Should have __CLR$R field", hasField(result, PHASE2_RECORDER_FIELD));

        // Verify <clinit> initializes the recorder
        List<String> clinitInstructions = getMethodInstructions(result, METHOD_NAME_CLINIT, METHOD_DESC_VOID);
        assertTrue("Should have getRecorder call in clinit",
            clinitInstructions.stream().anyMatch(s -> s.contains(OPCODE_INVOKESTATIC) && s.contains("Clover.getRecorder")));

        // Verify the method has GETSTATIC __CLR$R + LDC 100 + INVOKEVIRTUAL
        List<String> methodInstructions = getMethodInstructions(result, METHOD_NAME_GET_NAME, METHOD_DESC_VOID);
        assertTrue("Should have GETSTATIC for __CLR$R",
            methodInstructions.stream().anyMatch(s -> s.contains(OPCODE_GETSTATIC) && s.contains(TEST_CLASS_NAME) && s.contains(PHASE2_RECORDER_FIELD)));
        assertTrue("Should have LDC 100",
            methodInstructions.stream().anyMatch(s -> s.equals(LDC_100)));
        assertTrue(MSG_SHOULD_HAVE_INC,
            methodInstructions.stream().anyMatch(s -> s.contains(OPCODE_INVOKEVIRTUAL) && s.contains(COVERAGE_RECORDER_TYPE) && s.contains(METHOD_NAME_INC)));
    }

    @Test
    public void instrumentPreservesExistingMethods() {
        // Arrange: Generate a class with multiple methods
        byte[] classBytes = generateClassWithMultipleMethods();

        Map<String, Integer> methodIndices = new HashMap<>();
        methodIndices.put(METHOD_KEY_A, 10);
        // Note: methodB is NOT in the map

        BytecodeInstrumenter.RecorderConfig config = new BytecodeInstrumenter.RecorderConfig(
            TEST_DB_PATH, 1234L, 5678L, 100
        );
        BytecodeInstrumenter instrumenter = new BytecodeInstrumenter(config);

        // Act
        byte[] result = instrumenter.instrument(classBytes, methodIndices);

        // Assert
        assertNotNull(MSG_INSTRUMENTED_NOT_NULL, result);

        // Verify methodA is instrumented
        List<String> methodAInstructions = getMethodInstructions(result, METHOD_NAME_A, METHOD_DESC_VOID);
        assertTrue("methodA should have inc call",
            methodAInstructions.stream().anyMatch(s -> s.contains(OPCODE_INVOKEVIRTUAL) && s.contains(METHOD_NAME_INC)));

        // Verify methodB is NOT instrumented
        List<String> methodBInstructions = getMethodInstructions(result, METHOD_NAME_B, METHOD_DESC_VOID);
        assertTrue("methodB should NOT have inc call",
            methodBInstructions.stream().noneMatch(s -> s.contains(OPCODE_INVOKEVIRTUAL) && s.contains(METHOD_NAME_INC)));
    }

    @Test
    public void instrumentReturnsNullWhenNoMethodsToInstrument() {
        // Arrange
        byte[] classBytes = generateClassWithRecorder();
        Map<String, Integer> methodIndices = new HashMap<>(); // Empty map

        BytecodeInstrumenter.RecorderConfig config = new BytecodeInstrumenter.RecorderConfig(
            TEST_DB_PATH, 1234L, 5678L, 100
        );
        BytecodeInstrumenter instrumenter = new BytecodeInstrumenter(config);

        // Act
        byte[] result = instrumenter.instrument(classBytes, methodIndices);

        // Assert
        assertNull("Should return null when no methods to instrument", result);
    }

    @Test
    public void instrumentHandlesMultipleMethods() {
        // Arrange: Generate a class with 3 methods
        byte[] classBytes = generateClassWithMultipleMethods();

        Map<String, Integer> methodIndices = new HashMap<>();
        methodIndices.put(METHOD_KEY_A, 100);
        methodIndices.put(METHOD_KEY_B, 200);
        methodIndices.put(METHOD_KEY_C, 300);

        BytecodeInstrumenter.RecorderConfig config = new BytecodeInstrumenter.RecorderConfig(
            TEST_DB_PATH, 1234L, 5678L, 500
        );
        BytecodeInstrumenter instrumenter = new BytecodeInstrumenter(config);

        // Act
        byte[] result = instrumenter.instrument(classBytes, methodIndices);

        // Assert
        assertNotNull(MSG_INSTRUMENTED_NOT_NULL, result);

        // Verify methodA has index 100
        List<String> methodAInstructions = getMethodInstructions(result, METHOD_NAME_A, METHOD_DESC_VOID);
        assertTrue("methodA should have LDC 100",
            methodAInstructions.stream().anyMatch(s -> s.equals(LDC_100)));

        // Verify methodB has index 200
        List<String> methodBInstructions = getMethodInstructions(result, METHOD_NAME_B, METHOD_DESC_VOID);
        assertTrue("methodB should have LDC 200",
            methodBInstructions.stream().anyMatch(s -> s.equals(LDC_200)));

        // Verify methodC has index 300
        List<String> methodCInstructions = getMethodInstructions(result, METHOD_NAME_C, METHOD_DESC_VOID);
        assertTrue("methodC should have LDC 300",
            methodCInstructions.stream().anyMatch(s -> s.equals(LDC_300)));
    }

    /**
     * Generates a test class WITHOUT an inner __CLR class.
     */
    private byte[] generateClassWithoutRecorder() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        // Create outer class without inner class
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, TEST_CLASS_NAME, null, JAVA_LANG_OBJECT, null);

        // Add simple method getName()V
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, METHOD_NAME_GET_NAME, METHOD_DESC_VOID, null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();

        cw.visitEnd();

        return cw.toByteArray();
    }

    /**
     * Checks if a class has a specific field.
     */
    private boolean hasField(byte[] classBytes, String fieldName) {
        boolean[] result = new boolean[1];
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public FieldVisitor visitField(int access, String name, String descriptor, String signature, Object value) {
                if (name.equals(fieldName)) {
                    result[0] = true;
                }
                return super.visitField(access, name, descriptor, signature, value);
            }
        }, 0);
        return result[0];
    }

    /**
     * Generates a test class with multiple methods.
     */
    private byte[] generateClassWithMultipleMethods() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        // Create outer class
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, TEST_CLASS_NAME, null, JAVA_LANG_OBJECT, null);

        // Add inner class reference
        cw.visitInnerClass(RECORDER_OWNER, TEST_CLASS_NAME, RECORDER_INNER_NAME, Opcodes.ACC_STATIC);

        // Add methodA
        MethodVisitor mvA = cw.visitMethod(Opcodes.ACC_PUBLIC, METHOD_NAME_A, METHOD_DESC_VOID, null, null);
        mvA.visitCode();
        mvA.visitInsn(Opcodes.RETURN);
        mvA.visitMaxs(0, 1);
        mvA.visitEnd();

        // Add methodB
        MethodVisitor mvB = cw.visitMethod(Opcodes.ACC_PUBLIC, METHOD_NAME_B, METHOD_DESC_VOID, null, null);
        mvB.visitCode();
        mvB.visitInsn(Opcodes.RETURN);
        mvB.visitMaxs(0, 1);
        mvB.visitEnd();

        // Add methodC
        MethodVisitor mvC = cw.visitMethod(Opcodes.ACC_PUBLIC, METHOD_NAME_C, METHOD_DESC_VOID, null, null);
        mvC.visitCode();
        mvC.visitInsn(Opcodes.RETURN);
        mvC.visitMaxs(0, 1);
        mvC.visitEnd();

        cw.visitEnd();

        return cw.toByteArray();
    }

    /**
     * Generates a test class with an inner __CLR class that has a static recorder field.
     */
    private byte[] generateClassWithRecorder() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);

        // Create outer class
        cw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC, TEST_CLASS_NAME, null, JAVA_LANG_OBJECT, null);

        // Add inner class reference
        cw.visitInnerClass(RECORDER_OWNER, TEST_CLASS_NAME, RECORDER_INNER_NAME, Opcodes.ACC_STATIC);

        // Add simple method getName()V
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, METHOD_NAME_GET_NAME, METHOD_DESC_VOID, null, null);
        mv.visitCode();
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 1);
        mv.visitEnd();

        cw.visitEnd();

        // Now create the inner class with the recorder field
        ClassWriter innerCw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        innerCw.visit(Opcodes.V1_8, Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, RECORDER_OWNER, null, JAVA_LANG_OBJECT, null);

        // Add static field R
        FieldVisitor fv = innerCw.visitField(
            Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC,
            RECORDER_FIELD_NAME,
            COVERAGE_RECORDER_DESC,
            null,
            null
        );
        fv.visitEnd();

        innerCw.visitEnd();

        // For this test, we only return the outer class bytes
        // (In real scenario, both would be loaded, but we're testing the instrumenter's logic)
        return cw.toByteArray();
    }

    /**
     * Extracts instructions from a method for verification.
     */
    private List<String> getMethodInstructions(byte[] classBytes, String methodName, String descriptor) {
        List<String> instructions = new ArrayList<>();
        new ClassReader(classBytes).accept(new ClassVisitor(Opcodes.ASM9) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc, String sig, String[] exc) {
                if (name.equals(methodName) && desc.equals(descriptor)) {
                    return new MethodVisitor(Opcodes.ASM9) {
                        @Override
                        public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                            String opcodeStr = opcode == Opcodes.GETSTATIC ? OPCODE_GETSTATIC :
                                             opcode == Opcodes.PUTSTATIC ? "PUTSTATIC" : "FIELD";
                            instructions.add(opcodeStr + " " + owner + "." + name);
                        }

                        @Override
                        public void visitLdcInsn(Object value) {
                            instructions.add("LDC " + value);
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean isInterface) {
                            String opcodeStr = opcode == Opcodes.INVOKEVIRTUAL ? OPCODE_INVOKEVIRTUAL :
                                             opcode == Opcodes.INVOKESTATIC ? OPCODE_INVOKESTATIC : "INVOKE";
                            instructions.add(opcodeStr + " " + owner + "." + name);
                        }
                    };
                }
                return null;
            }
        }, 0);
        return instructions;
    }
}
