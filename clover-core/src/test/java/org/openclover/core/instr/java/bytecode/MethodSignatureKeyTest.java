package org.openclover.core.instr.java.bytecode;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class MethodSignatureKeyTest {

    private static final String GET_NAME = "getName";
    private static final String SET_NAME = "setName";
    private static final String FOO = "foo";
    private static final String INIT = "<init>";
    private static final String TO_STRING = "toString";
    private static final String NO_ARG_STRING_DESCRIPTOR = "()Ljava/lang/String;";

    @Test
    public void fromBytecodeCreatesCorrectKey() {
        MethodSignatureKey key = MethodSignatureKey.fromBytecode(GET_NAME, NO_ARG_STRING_DESCRIPTOR);

        assertEquals(GET_NAME, key.getName());
        assertEquals(0, key.getParamCount());
    }

    @Test
    public void fromBytecodeCountsParameters() {
        MethodSignatureKey key = MethodSignatureKey.fromBytecode(SET_NAME, "(Ljava/lang/String;)V");

        assertEquals(SET_NAME, key.getName());
        assertEquals(1, key.getParamCount());
    }

    @Test
    public void fromBytecodeHandlesMultipleParams() {
        MethodSignatureKey key = MethodSignatureKey.fromBytecode(FOO, "(ILjava/lang/String;J)V");

        assertEquals(FOO, key.getName());
        assertEquals(3, key.getParamCount());
    }

    @Test
    public void fromBytecodeHandlesConstructor() {
        MethodSignatureKey key = MethodSignatureKey.fromBytecode(INIT, "(Ljava/lang/String;I)V");

        assertEquals(INIT, key.getName());
        assertEquals(2, key.getParamCount());
    }

    @Test
    public void fromBytecodeHandlesNoArgVoid() {
        MethodSignatureKey key = MethodSignatureKey.fromBytecode(TO_STRING, NO_ARG_STRING_DESCRIPTOR);

        assertEquals(TO_STRING, key.getName());
        assertEquals(0, key.getParamCount());
    }

    @Test
    public void equalsAndHashCodeMatchOnNameAndParamCount() {
        MethodSignatureKey key1 = new MethodSignatureKey(GET_NAME, 0);
        MethodSignatureKey key2 = new MethodSignatureKey(GET_NAME, 0);

        assertEquals(key1, key2);
        assertEquals(key1.hashCode(), key2.hashCode());
    }

    @Test
    public void equalsReturnsFalseForDifferentParamCount() {
        MethodSignatureKey key1 = new MethodSignatureKey(GET_NAME, 0);
        MethodSignatureKey key2 = new MethodSignatureKey(GET_NAME, 1);

        assertEquals(false, key1.equals(key2));
    }

    @Test
    public void equalsReturnsFalseForDifferentName() {
        MethodSignatureKey key1 = new MethodSignatureKey(GET_NAME, 0);
        MethodSignatureKey key2 = new MethodSignatureKey(SET_NAME, 0);

        assertEquals(false, key1.equals(key2));
    }
}
