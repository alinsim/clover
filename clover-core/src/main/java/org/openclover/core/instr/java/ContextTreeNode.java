package org.openclover.core.instr.java;

import org.openclover.core.api.registry.ContextSet;

/**
 * Legacy stub for ANTLR-generated code compatibility.
 * This class is no longer used for instrumentation (JavaParser handles that now).
 * Kept only so generated JavaRecognizer.java compiles (even though it's never executed).
 */
public class ContextTreeNode {
    public ContextTreeNode(int size, ContextSet contextSet) {
        // Empty stub
    }

    public ContextTreeNode enterScope() {
        return new ContextTreeNode(0, null);
    }

    public ContextTreeNode enterContext(Object context) {
        return new ContextTreeNode(0, null);
    }

    public ContextTreeNode exitContext() {
        return this;
    }

    public ContextTreeNode exitScope() {
        return this;
    }

    public ContextSet getContext() {
        return null;
    }
}
