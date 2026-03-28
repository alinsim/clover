package org.openclover.core.instr.java;

import org.openclover.core.api.registry.MethodInfo;
import org.openclover.core.context.NamedContext;

import java.io.IOException;
import java.io.Writer;

/**
 * Legacy stub for ANTLR-generated code compatibility.
 * This class is no longer used for instrumentation (JavaParser handles that now).
 * Kept only so generated JavaRecognizer.java compiles (even though it's never executed).
 */
public class MethodEntryInstrEmitter implements Emitter {
    public MethodEntryInstrEmitter(MethodRegistrationNode node) {
        // Empty stub
    }

    public MethodInfo getMethod() {
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
