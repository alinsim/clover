package org.openclover.core.instr.java.bytecode;

import org.objectweb.asm.Type;
import org.openclover.core.api.registry.MethodInfo;

/**
 * Identifies a method by name + parameter count for comparison between
 * source-level registry (Phase 1) and bytecode (Phase 2).
 */
public final class MethodSignatureKey {
    private final String name;
    private final int paramCount;

    public MethodSignatureKey(String name, int paramCount) {
        this.name = name;
        this.paramCount = paramCount;
    }

    /** From Phase 1 source registry */
    public static MethodSignatureKey fromMethodInfo(MethodInfo method) {
        return new MethodSignatureKey(
            method.getSignature().getName(),
            method.getSignature().getParameters().length
        );
    }

    /** From Phase 2 bytecode (ASM) */
    public static MethodSignatureKey fromBytecode(String methodName, String descriptor) {
        return new MethodSignatureKey(
            methodName,
            Type.getArgumentTypes(descriptor).length
        );
    }

    public String getName() {
        return name;
    }

    public int getParamCount() {
        return paramCount;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        MethodSignatureKey that = (MethodSignatureKey) o;
        return paramCount == that.paramCount && name.equals(that.name);
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + paramCount;
        return result;
    }

    @Override
    public String toString() {
        return "MethodSignatureKey{" +
            "name='" + name + '\'' +
            ", paramCount=" + paramCount +
            '}';
    }
}
