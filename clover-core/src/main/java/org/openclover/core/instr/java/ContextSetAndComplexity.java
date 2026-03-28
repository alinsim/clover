package org.openclover.core.instr.java;

import org.openclover.core.api.registry.ContextSet;

/**
 * Legacy stub for ANTLR-generated code compatibility.
 * This class is no longer used for instrumentation (JavaParser handles that now).
 * Kept only so generated JavaRecognizer.java compiles (even though it's never executed).
 */
public class ContextSetAndComplexity {
    public final ContextSet set;
    public final int complexity;

    public ContextSetAndComplexity(ContextSet set, int complexity) {
        this.set = set;
        this.complexity = complexity;
    }

    public static ContextSetAndComplexity empty() {
        return new ContextSetAndComplexity(null, 0);
    }

    public static ContextSetAndComplexity ofComplexity(int complexity) {
        return new ContextSetAndComplexity(null, complexity);
    }

    public ContextSet getContext() {
        return set;
    }

    public int getComplexity() {
        return complexity;
    }

    public ContextSetAndComplexity setContext(ContextSet context) {
        return new ContextSetAndComplexity(context, complexity);
    }

    public ContextSetAndComplexity addComplexity(int additionalComplexity) {
        return new ContextSetAndComplexity(set, complexity + additionalComplexity);
    }
}
