package org.openclover.core.instr.java;

import antlr.Token;
import antlr.TokenStream;
import antlr.TokenStreamException;

/**
 * Legacy stub for ANTLR-generated code compatibility.
 * This class is no longer used for instrumentation (JavaParser handles that now).
 * Kept only so generated JavaRecognizer.java compiles (even though it's never executed).
 */
public class CloverTokenStreamFilter implements TokenStream {
    public CloverTokenStreamFilter(String filePath, Object lexer) {
        // Empty stub
    }

    @Override
    public Token nextToken() throws TokenStreamException {
        return null;
    }

    public CloverToken getInitialHiddenToken() {
        return null;
    }
}
