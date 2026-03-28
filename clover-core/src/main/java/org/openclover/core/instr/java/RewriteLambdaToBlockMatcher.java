package org.openclover.core.instr.java;

import java.util.Deque;

/**
 * Legacy stub for ANTLR-generated code compatibility.
 * This class is no longer used for instrumentation (JavaParser handles that now).
 * Kept only so generated JavaRecognizer.java compiles (even though it's never executed).
 */
public class RewriteLambdaToBlockMatcher {
    public RewriteLambdaToBlockMatcher() {
        // Empty stub
    }

    public boolean matches() {
        return false;
    }

    public static boolean shouldRewriteAsBlock(Deque<Deque<String>> identifiersStack) {
        return false;
    }
}
