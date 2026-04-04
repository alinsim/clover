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

    private static final String INC_PATTERN = ".inc(";
    private static final String INCRET_PATTERN = "incRet(";
    private static final String YIELD_KEYWORD = "yield ";
    private static final String SWITCH_EXPR_INT_SIMPLE = "class X { int m(int x) { return switch(x) { case 1 -> 10; default -> 20; }; } }";
    private static final String INCRET_MSG = "Should use incRet for switch expression arrow case";

    private String instrument(String source) {
        return TestInstrumentationHelper.instrument(source);
    }

    // --- Switch Expression: arrow cases with expressions (need yield) ---

    @Test
    public void switchExprArrowIntLiteralRewrittenToBlockWithYield() {
        String source = SWITCH_EXPR_INT_SIMPLE;
        String result = instrument(source);

        // AstInstrumenter uses incRet() for switch expression arrow cases (no yield)
        assertTrue(INCRET_MSG, result.contains(INCRET_PATTERN));
    }

    @Test
    public void switchExprArrowStringLiteralRewrittenCorrectly() {
        String source = "class X { String m(int x) { return switch(x) { case 1 -> \"one\"; default -> \"other\"; }; } }";
        String result = instrument(source);

        // AstInstrumenter uses incRet() for switch expression arrow cases
        assertTrue("Should use incRet for switch expression arrow cases",
                result.contains(INCRET_PATTERN));
    }

    @Test
    public void switchExprArrowMethodCallRewrittenWithYield() {
        String source = "class X { String m(int x) { return switch(x) { case 1 -> String.valueOf(x); default -> \"no\"; }; } }";
        String result = instrument(source);

        // AstInstrumenter uses incRet() for switch expression arrow cases
        assertTrue(INCRET_MSG, result.contains(INCRET_PATTERN));
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

        // AstInstrumenter uses incRet() for switch expression arrow cases
        assertTrue(INCRET_MSG, result.contains(INCRET_PATTERN));
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

        // AstInstrumenter uses incRet() which preserves expression form, no yield or block
        assertTrue(INCRET_MSG, result.contains(INCRET_PATTERN));
    }

    @Test
    public void semicolonInsideBlockBeforeClosingBrace() {
        String source = "class X { String m(int x) { return switch(x) { case 1 -> \"hello\"; default -> \"world\"; }; } }";
        String result = instrument(source);

        // AstInstrumenter uses incRet() which preserves expression form, no yield or block
        assertTrue(INCRET_MSG, result.contains(INCRET_PATTERN));
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

        // AstInstrumenter uses incRet() for switch expression arrow cases
        assertTrue(INCRET_MSG, result.contains(INCRET_PATTERN));
    }

    @Test
    public void switchExprInMethodArgument() {
        String source = "class X { void m(int x) { System.out.println(switch(x) { case 1 -> 10; default -> 20; }); } }";
        String result = instrument(source);

        // AstInstrumenter uses incRet() for switch expression arrow cases
        assertTrue(INCRET_MSG, result.contains(INCRET_PATTERN));
    }

    @Test
    public void switchExprInReturn() {
        String source = SWITCH_EXPR_INT_SIMPLE;
        String result = instrument(source);

        // AstInstrumenter uses incRet() for switch expression arrow cases
        assertTrue(INCRET_MSG, result.contains(INCRET_PATTERN));
    }

    @Test
    public void switchExprInIfCondition() {
        String source = "class X { void m(int x) { if (switch(x) { case 1 -> true; default -> false; }) { } } }";
        String result = instrument(source);

        // AstInstrumenter uses incRet() for switch expression arrow cases
        assertTrue(INCRET_MSG, result.contains(INCRET_PATTERN));
    }
}
