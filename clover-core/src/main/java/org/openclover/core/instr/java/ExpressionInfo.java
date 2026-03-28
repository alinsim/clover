package org.openclover.core.instr.java;

/**
 * Legacy stub for ANTLR-generated code compatibility.
 * This class is no longer used for instrumentation (JavaParser handles that now).
 * Kept only so generated JavaRecognizer.java compiles (even though it's never executed).
 */
public class ExpressionInfo {
    public ExpressionInfo(Object... args) {
        // Empty stub
    }

    public static ExpressionInfo fromTokens(CloverToken start, CloverToken end) {
        return new ExpressionInfo();
    }

    public int getComplexity() {
        return 0;
    }

    public boolean isConstant() {
        return false;
    }
}
