package org.openclover.core.instr.java;

import org.openclover.core.registry.entities.MethodSignature;

/**
 * Legacy stub for ANTLR-generated code compatibility.
 * This class is no longer used for instrumentation (JavaParser handles that now).
 * Kept only so generated JavaRecognizer.java compiles (even though it's never executed).
 */
public class MethodSignatureExt {
    public MethodSignatureExt(Object... args) {
        // Empty stub
    }

    public static MethodSignatureExt of(MethodSignature sig, CloverToken token, boolean deprecated) {
        return new MethodSignatureExt();
    }

    public static MethodSignatureExt of(Object o1, Object o2, Object o3) {
        return new MethodSignatureExt();
    }

    public MethodSignature signature() {
        return null;
    }

    public CloverToken endToken() {
        return null;
    }

    public boolean isDeprecated() {
        return false;
    }
}
