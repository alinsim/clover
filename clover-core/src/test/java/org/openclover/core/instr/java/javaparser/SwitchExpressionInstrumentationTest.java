package org.openclover.core.instr.java.javaparser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TDD tests for switch expression and switch statement arrow-case instrumentation.
 * Validates that:
 * 1. Arrow expression cases are rewritten to block form with yield
 * 2. Arrow statement cases (in switch statements) are rewritten to blocks
 * 3. Traditional colon cases are instrumented with R.inc() before first statement
 * 4. The rewritten code is syntactically valid (semicolons in correct positions)
 * 5. Block arrow cases get R.inc() inside the block
 */
public class SwitchExpressionInstrumentationTest {

    private static final String RECORDER_PREFIX = "__CLR.R";
    private static final String INIT_STRING = "/tmp/clover.db";
    private static final long VERSION = 1L;
    private static final String INC_PATTERN = ".inc(";
    private static final String YIELD_KEYWORD = "yield ";
    private static final String OPENING_BRACE = "{";
    private static final String BRACE_SEMI = "};";
    private static final String SWITCH_EXPR_INT_SIMPLE = "class X { int m(int x) { return switch(x) { case 1 -> 10; default -> 20; }; } }";

    private String instrument(String source) {
        return JavaParserInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING, VERSION);
    }

    // --- Switch Expression: arrow cases with expressions (need yield) ---

    @Test
    public void switchExprArrowIntLiteralRewrittenToBlockWithYield() {
        String source = SWITCH_EXPR_INT_SIMPLE;
        String result = instrument(source);

        // Must contain yield inside block: {R.inc(N);yield 10;}
        assertTrue("Should rewrite arrow case to block with yield",
                result.contains(OPENING_BRACE + RECORDER_PREFIX + INC_PATTERN) && result.contains(YIELD_KEYWORD));
        // The arrow case should produce "yield 10;}" — semicolon BEFORE closing brace
        assertTrue("Arrow case block should end with semicolon then brace",
                result.contains("10;}") || result.contains("10; }"));
    }

    @Test
    public void switchExprArrowStringLiteralRewrittenCorrectly() {
        String source = "class X { String m(int x) { return switch(x) { case 1 -> \"one\"; default -> \"other\"; }; } }";
        String result = instrument(source);

        assertTrue("Should contain yield for string case",
                result.contains("yield \"one\""));
        assertTrue("Should contain yield for default case",
                result.contains("yield \"other\""));
    }

    @Test
    public void switchExprArrowMethodCallRewrittenWithYield() {
        String source = "class X { String m(int x) { return switch(x) { case 1 -> String.valueOf(x); default -> \"no\"; }; } }";
        String result = instrument(source);

        assertTrue("Should contain yield for method call case",
                result.contains("yield String.valueOf(x)"));
    }

    @Test
    public void switchExprArrowThrowRewrittenWithoutYield() {
        String source = "class X { int m(int x) { return switch(x) { case 1 -> 10; default -> throw new RuntimeException(); }; } }";
        String result = instrument(source);

        // throw cases should NOT have yield
        assertFalse("Throw case should not have yield",
                result.contains("yield throw"));
    }

    @Test
    public void switchExprArrowBlockCaseInstrumentedInsideBlock() {
        String source = "class X { int m(int x) { return switch(x) { case 1 -> { yield 10; } default -> 20; }; } }";
        String result = instrument(source);

        // Block case: R.inc should be inside the existing block
        assertTrue("Block case should have inc inside block",
                result.contains(INC_PATTERN));
    }

    @Test
    public void switchExprMultipleValueCase() {
        String source = "class X { int m(int x) { return switch(x) { case 1, 2, 3 -> 10; default -> 20; }; } }";
        String result = instrument(source);

        assertTrue("Multi-value case should be rewritten with yield",
                result.contains(YIELD_KEYWORD));
    }

    // --- Switch Statement: arrow cases (no yield needed) ---

    @Test
    public void switchStmtArrowCaseRewrittenToBlockWithoutYield() {
        String source = "class X { void m(int x) { switch(x) { case 1 -> System.out.println(1); default -> System.out.println(0); } } }";
        String result = instrument(source);

        // Statement arrow cases should be rewritten to blocks WITHOUT yield
        assertFalse("Switch statement arrow should NOT have yield",
                result.contains(YIELD_KEYWORD));
        // But should have R.inc inside a block
        assertTrue("Should have inc call",
                result.contains(INC_PATTERN));
    }

    @Test
    public void switchStmtArrowBlockCaseInstrumented() {
        String source = "class X { void m(int x) { switch(x) { case 1 -> { System.out.println(1); } default -> { System.out.println(0); } } } }";
        String result = instrument(source);

        assertTrue("Block arrow case in switch statement should have inc",
                result.contains(INC_PATTERN));
    }

    // --- Traditional colon cases ---

    @Test
    public void switchStmtColonCaseInstrumentedBeforeFirstStatement() {
        String source = "class X { void m(int x) { switch(x) { case 1: System.out.println(1); break; default: System.out.println(0); } } }";
        String result = instrument(source);

        assertTrue("Colon case should have inc before first statement",
                result.contains(INC_PATTERN));
    }

    @Test
    public void switchExprColonCaseWithYieldInstrumented() {
        String source = "class X { int m(int x) { return switch(x) { case 1: yield 10; default: yield 20; }; } }";
        String result = instrument(source);

        assertTrue("Colon case with yield should have inc",
                result.contains(INC_PATTERN));
    }

    // --- Semicolon correctness (the critical bug) ---

    @Test
    public void noDoubleSemicolonInRewrittenArrowCase() {
        String source = SWITCH_EXPR_INT_SIMPLE;
        String result = instrument(source);

        // The rewritten form should be: {R.inc(N);yield 10;}
        // NOT: {R.inc(N);yield 10}; (missing ; before }) or {R.inc(N);yield 10;};  (extra ;)
        // Check specifically that "yield 10" is followed by ;} not by };
        assertTrue("yield value should be followed by semicolon-brace",
                result.contains("yield 10;}"));
        assertFalse("yield value should NOT have brace-semicolon (missing ; inside block)",
                result.contains("yield 10};"));
    }

    @Test
    public void semicolonInsideBlockBeforeClosingBrace() {
        String source = "class X { String m(int x) { return switch(x) { case 1 -> \"hello\"; default -> \"world\"; }; } }";
        String result = instrument(source);

        // The pattern should be: yield "hello";}  (semicolon before closing brace)
        assertTrue("Should have semicolon before closing brace",
                result.contains("\"hello\";}") || result.contains("\"hello\"; }"));
    }

    // --- No instrumentation in expression position ---

    @Test
    public void noStatementInsertionInArrowExpressionContext() {
        String source = SWITCH_EXPR_INT_SIMPLE;
        String result = instrument(source);

        // Should NOT have R.inc() directly before an expression in arrow context
        // (that would be invalid Java: case 1 -> R.inc(0);10;)
        assertFalse("Should not have inc followed by bare expression in arrow case",
                result.matches("(?s).*case.*->\\s*__CLR\\.R\\.inc\\(\\d+\\);\\s*\\d+;.*"));
    }

    // --- Switch in various contexts ---

    @Test
    public void switchExprInVariableAssignment() {
        String source = "class X { void m(int x) { int k = switch(x) { case 1 -> 10; default -> 20; }; } }";
        String result = instrument(source);

        assertTrue("Switch expression in assignment should be instrumented",
                result.contains(YIELD_KEYWORD));
    }

    @Test
    public void switchExprInMethodArgument() {
        String source = "class X { void m(int x) { System.out.println(switch(x) { case 1 -> 10; default -> 20; }); } }";
        String result = instrument(source);

        assertTrue("Switch expression in method argument should have yield",
                result.contains(YIELD_KEYWORD));
    }

    @Test
    public void switchExprInReturn() {
        String source = SWITCH_EXPR_INT_SIMPLE;
        String result = instrument(source);

        assertTrue("Switch expression in return should have yield",
                result.contains(YIELD_KEYWORD));
    }

    @Test
    public void switchExprInIfCondition() {
        String source = "class X { void m(int x) { if (switch(x) { case 1 -> true; default -> false; }) { } } }";
        String result = instrument(source);

        assertTrue("Switch expression in if condition should have yield",
                result.contains(YIELD_KEYWORD));
    }
}
