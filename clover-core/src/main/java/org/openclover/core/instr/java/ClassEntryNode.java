package org.openclover.core.instr.java;

import org.openclover.core.api.registry.ClassInfo;
import org.openclover.core.context.NamedContext;
import org.openclover.core.registry.entities.Modifiers;

import java.io.IOException;
import java.io.Writer;
import java.util.List;
import java.util.Map;

/**
 * Legacy stub for ANTLR-generated code compatibility.
 * This class is no longer used for instrumentation (JavaParser handles that now).
 * Kept only so generated JavaRecognizer.java compiles (even though it's never executed).
 */
public class ClassEntryNode implements Emitter {
    public ClassEntryNode(Map<String, List<String>> tags, Modifiers mods, String fullName,
                          String packageName, String superclass, List<String> interfaces,
                          boolean isInterface, boolean isAnnotation, boolean isEnum,
                          CloverToken startToken, int startLine, int startColumn) {
        // Empty stub
    }

    public ClassEntryNode(Map<String, List<String>> tags, Modifiers mods, String fullName,
                          String packageName, String superclass, Object contextSet, int line, int col,
                          boolean isInterface, boolean isAnnotation, boolean isEnum, boolean isRecord) {
        // Empty stub - alternate constructor signature
    }

    public void setRecorderInstrEmitter(RecorderInstrEmitter emitter) {
        // No-op
    }

    public CloverToken getRecorderInsertPoint() {
        return new CloverToken();
    }

    public void setRecorderInsertPoint(CloverToken token) {
        // No-op
    }

    public ClassInfo getDataNode() {
        return null;
    }

    @Override
    public void emit(Writer out) throws IOException {
        // No-op
    }

    @Override
    public void setEnabled(boolean enabled) {
        // No-op
    }

    @Override
    public void addContext(NamedContext context) {
        // No-op
    }

    @Override
    public void initialise(InstrumentationState state) {
        // No-op
    }
    @Override
    public void addDependent(Emitter dependent) {
        // No-op
    }
}
