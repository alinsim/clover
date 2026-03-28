package org.openclover.core.instr.java.javaparser;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link JavaParserInstrumenter} — validates the end-to-end
 * JavaParser-based instrumentation pipeline.
 */
public class JavaParserInstrumenterTest {

    private static final String RECORDER_PREFIX = "__CLR4_1_100hckkb3w8.R";
    private static final String INIT_STRING = "/tmp/clover.db";
    private static final long REGISTRY_VERSION = 1L;
    private static final String OPENCLOVER_MARKER = "/* $$ This file has been instrumented by OpenClover";
    private static final String INC_PREFIX = "__CLR4_1_100hckkb3w8.R.inc(";
    private static final String INC_0 = INC_PREFIX + "0)";
    private static final String INC_1 = INC_PREFIX + "1)";
    private static final String INC_2 = INC_PREFIX + "2)";
    private static final String MSG_INC_0_METHOD = "Should contain inc(0) for method entry";
    private static final String MSG_INC_1_BRANCH = "Should contain inc(1) for then branch";
    private static final String MSG_INC_1_LOOP = "Should contain inc(1) for loop body";
    private static final String MSG_INC_2_STMT = "Should contain inc(2) for statement";
    private static final String MSG_MULTIPLE_INC = "Should contain multiple inc calls";

    @Test
    public void instrumentSimpleClassWithOneMethod() {
        String source = "class Foo {\n    void bar() {\n        System.out.println();\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        assertTrue("Should start with OpenClover marker", result.startsWith(OPENCLOVER_MARKER));
        assertTrue("Should contain recorder class", result.contains("public static class __CLR4_1_100hckkb3w8"));
        assertTrue("Should contain method entry inc call", result.contains(INC_PREFIX));
    }

    @Test
    public void instrumentClassWithMultipleMethods() {
        String source = "class Foo {\n    void bar() {\n    }\n    void baz() {\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        assertTrue("Should contain inc(0)", result.contains(INC_0));
        assertTrue("Should contain inc(1)", result.contains(INC_1));
    }

    @Test
    public void instrumentClassWithConstructor() {
        String source = "class Foo {\n    Foo() {\n        super();\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        assertTrue("Should contain constructor entry inc call", result.contains(INC_PREFIX));
    }

    @Test
    public void instrumentPreservesOriginalCode() {
        String source = "class Foo {\n    void bar() {\n        int x = 42;\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        assertTrue("Should preserve original statement", result.contains("int x = 42;"));
    }

    @Test
    public void countInstrumentationPointsForTwoMethods() {
        String source = "class Foo {\n    void a() {}\n    void b() {}\n}";

        int count = JavaParserInstrumenter.countInstrumentationPoints(source, RECORDER_PREFIX);
        assertEquals("Should find 2 method entries", 2, count);
    }

    @Test
    public void countInstrumentationPointsForMethodAndConstructor() {
        String source = "class Foo {\n    Foo() {}\n    void bar() {}\n}";

        int count = JavaParserInstrumenter.countInstrumentationPoints(source, RECORDER_PREFIX);
        assertEquals("Should find 2 entries (constructor + method)", 2, count);
    }

    @Test
    public void instrumentInterfaceSkipsRecorderInjection() {
        String source = "interface Foo {\n    void bar();\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        assertTrue("Should have marker", result.startsWith(OPENCLOVER_MARKER));
        // Interface has no recorder class since it has no body to instrument
        assertTrue("Should not contain inc calls for abstract methods",
                !result.contains(INC_PREFIX));
    }

    @Test
    public void instrumentNestedClass() {
        String source = "class Outer {\n    class Inner {\n        void foo() {}\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        assertTrue("Should contain inc call for inner method", result.contains(INC_PREFIX));
    }

    @Test
    public void instrumentRecorderContainsInitString() {
        String result = JavaParserInstrumenter.instrument(
                "class Foo {}", RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        assertTrue("Recorder should reference the init string",
                result.contains(INIT_STRING));
    }

    @Test
    public void instrumentStatementCoverageForExpressions() {
        String source = "class Foo {\n    void bar() {\n        int x = 5;\n        System.out.println(x);\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        // Should have method entry (inc(0)) and two statements (inc(1), inc(2))
        assertTrue(MSG_INC_0_METHOD, result.contains(INC_0));
        assertTrue("Should contain inc(1) for first statement", result.contains(INC_1));
        assertTrue("Should contain inc(2) for second statement", result.contains(INC_2));
    }

    @Test
    public void instrumentReturnStatement() {
        String source = "class Foo {\n    int bar() {\n        return 42;\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        // Should have method entry and return statement
        assertTrue("Should instrument return statement", result.contains(INC_1));
    }

    @Test
    public void instrumentThrowStatement() {
        String source = "class Foo {\n    void bar() {\n        throw new RuntimeException();\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        // Should have method entry and throw statement
        assertTrue("Should instrument throw statement", result.contains(INC_1));
    }

    @Test
    public void instrumentIfElseBranches() {
        String source = "class Foo {\n    void bar(boolean b) {\n        if (b) {\n            System.out.println(\"then\");\n        } else {\n            System.out.println(\"else\");\n        }\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        // Should have method entry, then branch, else branch, and two statements
        assertTrue(MSG_INC_0_METHOD, result.contains(INC_0));
        assertTrue(MSG_INC_1_BRANCH, result.contains(INC_1));
        assertTrue("Should contain inc(2) for else branch", result.contains(INC_2));
        assertTrue("Should contain inc(3) for then statement", result.contains(INC_PREFIX + "3)"));
        assertTrue("Should contain inc(4) for else statement", result.contains(INC_PREFIX + "4)"));
    }

    @Test
    public void instrumentIfWithoutElse() {
        String source = "class Foo {\n    void bar(boolean b) {\n        if (b) {\n            System.out.println(\"then\");\n        }\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        // Should have method entry, then branch, and statement
        assertTrue(MSG_INC_0_METHOD, result.contains(INC_0));
        assertTrue(MSG_INC_1_BRANCH, result.contains(INC_1));
        assertTrue(MSG_INC_2_STMT, result.contains(INC_2));
    }

    @Test
    public void instrumentSwitchCases() {
        String source = "class Foo {\n    void bar(int x) {\n        switch (x) {\n            case 1:\n                System.out.println(\"one\");\n                break;\n            case 2:\n                System.out.println(\"two\");\n                break;\n            default:\n                System.out.println(\"other\");\n        }\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        // Should have method entry, three case branches, three statements, and two breaks
        assertTrue(MSG_MULTIPLE_INC, result.contains(INC_0));
        assertTrue("Should instrument case branches", result.contains(INC_1));
    }

    @Test
    public void instrumentWhileLoop() {
        String source = "class Foo {\n    void bar() {\n        while (true) {\n            System.out.println(\"loop\");\n        }\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        // Should have method entry, loop body branch, and statement
        assertTrue(MSG_INC_0_METHOD, result.contains(INC_0));
        assertTrue(MSG_INC_1_LOOP, result.contains(INC_1));
        assertTrue(MSG_INC_2_STMT, result.contains(INC_2));
    }

    @Test
    public void instrumentForLoop() {
        String source = "class Foo {\n    void bar() {\n        for (int i = 0; i < 10; i++) {\n            System.out.println(i);\n        }\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        // Should have method entry, loop body branch, and statement
        assertTrue(MSG_INC_0_METHOD, result.contains(INC_0));
        assertTrue(MSG_INC_1_LOOP, result.contains(INC_1));
        assertTrue(MSG_INC_2_STMT, result.contains(INC_2));
    }

    @Test
    public void instrumentRespectsCloverOff() {
        String source = "class Foo {\n    void bar() {\n        System.out.println(\"before\");\n        // CLOVER:OFF\n        System.out.println(\"disabled\");\n        // CLOVER:ON\n        System.out.println(\"after\");\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        int count = JavaParserInstrumenter.countInstrumentationPoints(source, RECORDER_PREFIX);
        // Should have method entry (1), before statement (1), after statement (1) = 3
        // The disabled statement should NOT be counted
        assertEquals("Should have 3 instrumentation points (excluding disabled)", 3, count);

        // Verify the structure
        assertTrue(MSG_INC_0_METHOD, result.contains(INC_0));
        assertTrue("Should contain inc(1) for before statement", result.contains(INC_1));
        assertTrue("Should contain inc(2) for after statement", result.contains(INC_2));
    }

    @Test
    public void instrumentRespectsCloverOnRestore() {
        String source = "class Foo {\n    void first() {\n        System.out.println(\"1\");\n    }\n    // CLOVER:OFF\n    void disabled() {\n        System.out.println(\"disabled\");\n    }\n    // CLOVER:ON\n    void restored() {\n        System.out.println(\"2\");\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        int count = JavaParserInstrumenter.countInstrumentationPoints(source, RECORDER_PREFIX);
        // Should have first method entry (1) + statement (1) + restored method entry (1) + statement (1) = 4
        assertEquals("Should have 4 instrumentation points", 4, count);
    }

    @Test
    public void countInstrumentationPointsIncludesStatements() {
        String source = "class Foo {\n    void bar() {\n        int x = 1;\n        int y = 2;\n        System.out.println(x + y);\n    }\n}";

        int count = JavaParserInstrumenter.countInstrumentationPoints(source, RECORDER_PREFIX);
        // Should have method entry (1) + three statements (3) = 4
        assertEquals("Should count method entry and statements", 4, count);
    }

    @Test
    public void countInstrumentationPointsIncludesBranches() {
        String source = "class Foo {\n    void bar(boolean b) {\n        if (b) {\n            System.out.println(\"yes\");\n        }\n    }\n}";

        int count = JavaParserInstrumenter.countInstrumentationPoints(source, RECORDER_PREFIX);
        // Should have method entry (1) + if-then branch (1) + statement (1) = 3
        assertEquals("Should count method entry, branch, and statement", 3, count);
    }

    @Test
    public void instrumentBreakAndContinue() {
        String source = "class Foo {\n    void bar() {\n        for (int i = 0; i < 10; i++) {\n            if (i == 5) break;\n            if (i == 3) continue;\n        }\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        // Verify break and continue are instrumented
        assertTrue(MSG_MULTIPLE_INC, result.contains(INC_PREFIX));

        int count = JavaParserInstrumenter.countInstrumentationPoints(source, RECORDER_PREFIX);
        // Method entry (1) + for body (1) + if-then (1) + break (1) + if-then (1) + continue (1) = 6
        assertEquals("Should count all instrumentation points", 6, count);
    }

    @Test
    public void instrumentAssertStatement() {
        String source = "class Foo {\n    void bar(int x) {\n        assert x > 0;\n    }\n}";

        String result = JavaParserInstrumenter.instrument(
                source, RECORDER_PREFIX, INIT_STRING, REGISTRY_VERSION);

        int count = JavaParserInstrumenter.countInstrumentationPoints(source, RECORDER_PREFIX);
        // Method entry (1) + assert statement (1) = 2
        assertEquals("Should count method entry and assert", 2, count);
    }
}
