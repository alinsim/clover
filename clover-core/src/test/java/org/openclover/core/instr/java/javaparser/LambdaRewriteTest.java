package org.openclover.core.instr.java.javaparser;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TDD tests for lambda instrumentation edge cases.
 * Expression lambdas use lambdaInc() wrapping in safe contexts (variable initializer,
 * assignment) and are skipped in unsafe contexts (method args, casts, returns).
 * Block lambdas get R.inc() after opening brace.
 */
public class LambdaRewriteTest {

    private static final String PREFIX = "__CLR.R";
    private static final String INIT = "/tmp/clover.db";
    private static final long VER = 1L;
    private static final String INC = ".inc(";
    private static final String LAMBDA_INC = "lambdaInc(";
    private static final String CLASS_X_OPEN = "class X {\n";
    private static final String VOID_TEST_OPEN = "    void test() {\n";
    private static final String BRACE_CLOSE_NL = "    }\n";
    private static final String IMPORT_ARRAYS = "import java.util.Arrays;\n";
    private static final String EMPTY_BLOCK_SEMI = "{;}";
    private static final String IFACE_F = "    interface F { int apply(int x); }\n";
    private static final String FOREACH_PRINTLN = "    void test() { Arrays.asList(1,2,3).forEach(x -> System.out.println(x)); }\n";

    private String instrument(String source) {
        return JavaParserInstrumenter.instrument(source, PREFIX, INIT, VER);
    }

    // --- Expression lambdas in safe contexts: wrapped with lambdaInc ---

    @Test
    public void expressionLambdaInVariableInitializerWrappedWithLambdaInc() {
        String source = CLASS_X_OPEN +
                IFACE_F +
                "    void test() { F f = x -> x + 1; }\n" +
                "}";
        String result = instrument(source);
        assertTrue("Variable initializer lambda should use lambdaInc",
                result.contains(LAMBDA_INC));
    }

    @Test
    public void expressionLambdaInAssignmentWrappedWithLambdaInc() {
        String source = CLASS_X_OPEN +
                IFACE_F +
                VOID_TEST_OPEN +
                "        F f = null;\n" +
                "        f = x -> x + 1;\n" +
                BRACE_CLOSE_NL +
                "}";
        String result = instrument(source);
        assertTrue("Assignment lambda should use lambdaInc",
                result.contains(LAMBDA_INC));
    }

    // --- Expression lambdas in unsafe contexts: NOT wrapped (skip) ---

    @Test
    public void expressionLambdaInMethodArgumentSkipped() {
        String source = IMPORT_ARRAYS +
                CLASS_X_OPEN +
                FOREACH_PRINTLN +
                "}";
        String result = instrument(source);
        // Method argument is unsafe for lambdaInc — skip wrapping
        assertFalse("Method arg lambda should NOT use lambdaInc",
                result.contains("forEach(" + LAMBDA_INC));
    }

    @Test
    public void expressionLambdaInCastSkipped() {
        String source = "import java.util.List;\nimport java.util.ArrayList;\n" +
                "import java.util.stream.Collectors;\n" +
                CLASS_X_OPEN +
                "    interface TypeOne { String apply(String a); }\n" +
                VOID_TEST_OPEN +
                "        List<String> list = new ArrayList<>();\n" +
                "        list.stream().map((TypeOne) (a) -> a).collect(Collectors.toList());\n" +
                BRACE_CLOSE_NL +
                "}";
        String result = instrument(source);
        // Cast context is unsafe — must not produce {;} or corrupt positions
        assertFalse("Cast lambda should not produce empty block",
                result.contains(EMPTY_BLOCK_SEMI));
    }

    // --- Block lambdas: R.inc() after opening brace ---

    @Test
    public void blockLambdaGetsIncAfterBrace() {
        String source = CLASS_X_OPEN +
                "    void test() { Runnable r = () -> { System.out.println(); }; }\n" +
                "}";
        String result = instrument(source);
        assertTrue("Block lambda should have inc after brace",
                result.contains(INC));
    }

    // --- Method references: lambdaInc in safe contexts only ---

    @Test
    public void methodReferenceInVariableInitializerWrapped() {
        String source = CLASS_X_OPEN +
                "    interface F { String apply(String s); }\n" +
                "    void test() { F f = String::toUpperCase; }\n" +
                "}";
        String result = instrument(source);
        assertTrue("Variable initializer method ref should use lambdaInc",
                result.contains(LAMBDA_INC));
    }

    // --- Void expression lambdas should NOT be rewritten to block form ---

    @Test
    public void voidLambdaNotRewrittenToBlock() {
        // forEach(x -> println(x)) — void lambda
        // Must NOT be rewritten to block form (would need to decide return/no-return)
        String source = IMPORT_ARRAYS +
                CLASS_X_OPEN +
                FOREACH_PRINTLN +
                "}";
        String result = instrument(source);
        // Should NOT contain block rewrite pattern
        assertFalse("Void lambda should not be rewritten to block",
                result.contains("-> {__CLR"));
    }
}
