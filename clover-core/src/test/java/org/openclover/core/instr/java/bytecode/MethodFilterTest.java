package org.openclover.core.instr.java.bytecode;

import org.junit.Test;
import org.objectweb.asm.Opcodes;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MethodFilterTest {

    private static final String CLINIT = "<clinit>";
    private static final String INIT = "<init>";
    private static final String GET_NAME = "getName";
    private static final String HELPER = "helper";
    private static final String EMPTY_DESCRIPTOR = "()V";

    private final MethodFilter filter = new MethodFilter();

    @Test
    public void excludesBridgeMethods() {
        boolean result = filter.shouldInstrument(Opcodes.ACC_BRIDGE, GET_NAME, EMPTY_DESCRIPTOR);

        assertFalse(result);
    }

    @Test
    public void excludesSyntheticMethods() {
        boolean result = filter.shouldInstrument(Opcodes.ACC_SYNTHETIC, GET_NAME, EMPTY_DESCRIPTOR);

        assertFalse(result);
    }

    @Test
    public void excludesStaticInitializer() {
        boolean result = filter.shouldInstrument(Opcodes.ACC_STATIC, CLINIT, EMPTY_DESCRIPTOR);

        assertFalse(result);
    }

    @Test
    public void excludesAbstractMethods() {
        boolean result = filter.shouldInstrument(Opcodes.ACC_ABSTRACT, GET_NAME, EMPTY_DESCRIPTOR);

        assertFalse(result);
    }

    @Test
    public void excludesNativeMethods() {
        boolean result = filter.shouldInstrument(Opcodes.ACC_NATIVE, GET_NAME, EMPTY_DESCRIPTOR);

        assertFalse(result);
    }

    @Test
    public void allowsRegularPublicMethod() {
        boolean result = filter.shouldInstrument(Opcodes.ACC_PUBLIC, GET_NAME, EMPTY_DESCRIPTOR);

        assertTrue(result);
    }

    @Test
    public void allowsPrivateMethod() {
        boolean result = filter.shouldInstrument(Opcodes.ACC_PRIVATE, HELPER, EMPTY_DESCRIPTOR);

        assertTrue(result);
    }

    @Test
    public void allowsConstructor() {
        boolean result = filter.shouldInstrument(Opcodes.ACC_PUBLIC, INIT, EMPTY_DESCRIPTOR);

        assertTrue(result);
    }
}
