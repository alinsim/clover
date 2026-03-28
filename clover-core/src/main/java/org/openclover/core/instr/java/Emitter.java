package org.openclover.core.instr.java;

import org.openclover.core.context.NamedContext;

import java.io.IOException;
import java.io.Writer;

/**
 * Legacy stub for ANTLR-generated code compatibility.
 * This interface is no longer used for instrumentation (JavaParser handles that now).
 * Kept only so generated JavaRecognizer.java compiles (even though it's never executed).
 */
public interface Emitter {
    void emit(Writer out) throws IOException;
    void setEnabled(boolean enabled);
    void addContext(NamedContext context);
    void initialise(InstrumentationState state);
    void addDependent(Emitter dependent);
}
