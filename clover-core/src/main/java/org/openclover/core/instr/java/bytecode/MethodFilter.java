package org.openclover.core.instr.java.bytecode;

import org.objectweb.asm.Opcodes;

/**
 * Filters bytecode methods to exclude those that aren't worth instrumenting:
 * bridge methods, synthetic accessors, static initializers.
 */
public final class MethodFilter {

    private static final String CLINIT = "<clinit>";

    public boolean shouldInstrument(int access, String name, String descriptor) {
        // Exclude bridge methods (type erasure delegates)
        if ((access & Opcodes.ACC_BRIDGE) != 0) {
            return false;
        }
        // Exclude synthetic accessors (access$000 etc.)
        if ((access & Opcodes.ACC_SYNTHETIC) != 0) {
            return false;
        }
        // Exclude static initializers
        if (CLINIT.equals(name)) {
            return false;
        }
        // Exclude abstract/native methods (no body to instrument)
        if ((access & Opcodes.ACC_ABSTRACT) != 0) {
            return false;
        }
        if ((access & Opcodes.ACC_NATIVE) != 0) {
            return false;
        }
        return true;
    }
}
