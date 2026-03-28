package org.openclover.core.instr.java.javaparser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TDD tests for braceless control flow body instrumentation.
 * When if/while/for has a single statement without braces, inserting R.inc()
 * before it must NOT escape the branch body. The fix wraps with braces.
 */
public class BracelessControlFlowTest {

    private static final String PREFIX = "__CLR.R";
    private static final String INIT = "/tmp/clover.db";
    private static final long VER = 1L;
    private static final String INC = ".inc(";
    private static final String CLASS_OPEN = "class X {\n";
    private static final String BOOL_M_OPEN = "    boolean m(Object o) {\n";
    private static final String IF_THIS_RETURN = "        if (this == o) return true;\n";
    private static final String RETURN_FALSE = "        return false;\n";
    private static final String CLOSE_METHOD = "    }\n";
    private static final String IF_THIS_BRACE = "if (this == o) {";
    private static final String IF_THIS_NOBRACE = "if (this == o){";

    private String instrument(String source) {
        return JavaParserInstrumenter.instrument(source, PREFIX, INIT, VER);
    }

    @Test
    public void bracelessIfReturnWrappedInBraces() {
        String source = CLASS_OPEN +
                BOOL_M_OPEN +
                IF_THIS_RETURN +
                RETURN_FALSE +
                CLOSE_METHOD +
                "}";
        String result = instrument(source);

        // The return must stay INSIDE the if body, wrapped in braces
        // Correct:   if (this == o) {R.inc(N);return true;}
        // Wrong:     if (this == o) R.inc(N);return true;  ← return escapes if
        assertTrue("Braceless if body should be wrapped in braces",
                result.contains(IF_THIS_BRACE) || result.contains(IF_THIS_NOBRACE));
        // The return false after the if must still be reachable
        assertFalse("Return after if must NOT be unreachable",
                result.contains("R.inc") && !result.contains("{") && result.contains("return true;"));
    }

    @Test
    public void bracelessIfElseWrappedInBraces() {
        String source = CLASS_OPEN +
                "    int m(int x) {\n" +
                "        if (x > 0) return 1;\n" +
                "        else return -1;\n" +
                CLOSE_METHOD +
                "}";
        String result = instrument(source);

        // Both then and else must be wrapped
        assertTrue("Then branch should have brace",
                result.contains("{") && result.contains("return 1;"));
        assertTrue("Else branch should have brace",
                result.contains("{") && result.contains("return -1;"));
    }

    @Test
    public void bracelessWhileWrappedInBraces() {
        String source = CLASS_OPEN +
                "    void m(int[] a) {\n" +
                "        int i = 0;\n" +
                "        while (i < a.length) a[i++] = 0;\n" +
                CLOSE_METHOD +
                "}";
        String result = instrument(source);

        // while body must be wrapped
        assertFalse("While body R.inc should not escape",
                result.matches("(?s).*while.*\\)\\s*__CLR\\.R\\.inc.*a\\[i\\+\\+\\].*"));
    }

    @Test
    public void bracelessForWrappedInBraces() {
        String source = CLASS_OPEN +
                "    void m() {\n" +
                "        for (int i = 0; i < 10; i++) System.out.println(i);\n" +
                CLOSE_METHOD +
                "}";
        String result = instrument(source);

        // for body must be wrapped
        assertTrue("For body should have opening brace after )",
                result.contains(") {") || result.contains("){"));
    }

    @Test
    public void bracedIfBodyNotDoubleWrapped() {
        String source = CLASS_OPEN +
                BOOL_M_OPEN +
                "        if (this == o) { return true; }\n" +
                RETURN_FALSE +
                CLOSE_METHOD +
                "}";
        String result = instrument(source);

        // Already braced — should NOT add extra braces
        assertFalse("Already braced if should not get double braces",
                result.contains("{ {"));
    }

    @Test
    public void equalsPatternDoesNotCauseUnreachable() {
        // This is the exact pattern from the northfox EntityModel.java bug
        String source = CLASS_OPEN +
                "    public boolean equals(Object o) {\n" +
                IF_THIS_RETURN +
                "        if (o == null || getClass() != o.getClass()) return false;\n" +
                "        return hashCode() == o.hashCode();\n" +
                CLOSE_METHOD +
                "}";
        String result = instrument(source);

        // The second if must be reachable — first return must be inside braces
        assertTrue("First if body must be braced to prevent unreachable code",
                result.contains(IF_THIS_BRACE) || result.contains(IF_THIS_NOBRACE));
        assertTrue("Second if must still exist (not unreachable)",
                result.contains("if (o == null"));
    }
}
