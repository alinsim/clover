package org.openclover.core.instr.java;

import org.openclover.core.context.NamedContext;

import java.io.IOException;
import java.io.Writer;

/**
 * Legacy stub for ANTLR-generated code compatibility.
 * This class is no longer used for instrumentation (JavaParser handles that now).
 * Kept only so generated JavaRecognizer.java compiles (even though it's never executed).
 */
public class StartBoolInstrEmitter implements Emitter {
    public StartBoolInstrEmitter() {
        // Empty stub
    }

    public StartBoolInstrEmitter(Object... args) {
        // Empty stub - accepts any constructor arguments
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
