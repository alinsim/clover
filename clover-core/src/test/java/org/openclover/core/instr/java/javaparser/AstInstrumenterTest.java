package org.openclover.core.instr.java.javaparser;

import com.github.javaparser.StaticJavaParser;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TDD tests for the AST-based instrumenter.
 * Defines the expected API and behavior for each instrumentation pattern.
 *
 * The AstInstrumenter replaces the text-based Insertion+SourceRewriter approach
 * with direct AST modification + LexicalPreservingPrinter output.
 */
@SuppressWarnings("DuplicateStringLiteralInspection")
public class AstInstrumenterTest {

    private static final String RECORDER_PREFIX = "__CLR";
    private static final String INIT_STRING = "/tmp/test.db";
    private static final String INC_MARKER = "__CLR.inc(";
    private static final String RECORDER_CLASS = "static class __CLR";
    private static final String INSTRUMENTATION_MARKER = "This file has been instrumented";
    private static final String DO_WORK = "doWork()";

    /**
     * The most basic contract: instrument a simple class and get valid output.
     */
    @Test
    public void instrumentSimpleClassProducesValidOutput() {
        String source = "class Foo { void bar() { System.out.println(\"hello\"); } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertNotNull("Output should not be null", output);
        assertFalse("Output should not be empty", output.isEmpty());
        assertTrue("Output should contain instrumentation marker", output.contains(INSTRUMENTATION_MARKER));
        assertTrue("Output should contain R.inc() calls", output.contains(INC_MARKER));
        assertTrue("Output should contain recorder class", output.contains(RECORDER_CLASS));
        assertTrue("Output should preserve original code", output.contains("System.out.println"));
        assertParseable(output);
    }

    /**
     * Method entry: R.inc(N) as first statement in method body.
     */
    @Test
    public void instrumentsMethodEntry() {
        String source = "class Foo { void bar() { doWork(); } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        // R.inc should appear before doWork
        int incPos = output.indexOf(INC_MARKER);
        int workPos = output.indexOf(DO_WORK);
        assertTrue("R.inc should appear before doWork", incPos > 0 && incPos < workPos);
        assertParseable(output);
    }

    /**
     * Statement instrumentation: R.inc(N) before each executable statement.
     */
    @Test
    public void instrumentsStatements() {
        String source = "class Foo { void bar() { int x = 1; int y = 2; int z = 3; } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        // Should have at least 4 R.inc calls: 1 method entry + 3 statements
        int count = countOccurrences(output, INC_MARKER);
        assertTrue("Should have at least 4 R.inc calls (method + 3 stmts)", count >= 4);
        assertParseable(output);
    }

    /**
     * If-then-else: true branch R.inc(N), false branch R.inc(N+1), consecutive indices.
     */
    @Test
    public void instrumentsIfElseBranches() {
        String source = "class Foo { void bar(boolean b) { if (b) { doA(); } else { doB(); } } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        String doA = "doA()";
        String doB = "doB()";
        assertTrue("Should contain R.inc in then block", output.contains(doA));
        assertTrue("Should contain R.inc in else block", output.contains(doB));
        // Both branches should have their own R.inc calls
        int incBeforeDoA = output.lastIndexOf(INC_MARKER, output.indexOf(doA));
        int incBeforeDoB = output.lastIndexOf(INC_MARKER, output.indexOf(doB));
        assertTrue("then block should have R.inc before doA", incBeforeDoA > 0);
        assertTrue("else block should have R.inc before doB", incBeforeDoB > 0);
        assertParseable(output);
    }

    /**
     * If-then without else: synthetic else with false branch R.inc(N+1).
     */
    @Test
    public void instrumentsIfWithoutElseAddsSyntheticElse() {
        String source = "class Foo { void bar(boolean b) { if (b) { doSomething(); } } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Should contain synthetic else", output.contains("else"));
        assertParseable(output);
    }

    /**
     * Braceless if body: wrapped in block with R.inc.
     */
    @Test
    public void instrumentsBracelessIfBody() {
        String source = "class Foo { boolean bar(boolean b) { if (b) return true; return false; } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Should contain return true", output.contains("return true"));
        assertTrue("Should contain return false", output.contains("return false"));
        assertTrue("Braceless body should be wrapped", output.contains("{") && output.contains("}"));
        assertParseable(output);
    }

    /**
     * Loop instrumentation: R.inc in loop body.
     */
    @Test
    public void instrumentsLoopBody() {
        String source = "class Foo { void bar() { while (true) { doWork(); } } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        int incPos = output.lastIndexOf(INC_MARKER, output.indexOf(DO_WORK));
        assertTrue("Loop body should have R.inc before doWork", incPos > 0);
        assertParseable(output);
    }

    /**
     * Constructor instrumentation: R.inc after super()/this() call.
     */
    @Test
    public void instrumentsConstructor() {
        String source = "class Foo { Foo() { System.out.println(\"ctor\"); } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Should contain R.inc in constructor", output.contains(INC_MARKER));
        assertParseable(output);
    }

    /**
     * Recorder class: generated as inner static class.
     */
    @Test
    public void generatesRecorderClass() {
        String source = "class Foo { void bar() {} }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Should contain recorder inner class", output.contains(RECORDER_CLASS));
        assertParseable(output);
    }

    /**
     * Output preserves original formatting of non-instrumented code.
     */
    @Test
    public void preservesOriginalFormatting() {
        String source =
                "package com.example;\n\n"
                + "// A comment\n"
                + "public class Foo {\n"
                + "    /** Javadoc */\n"
                + "    public void bar() {\n"
                + "        System.out.println(\"hello\");\n"
                + "    }\n"
                + "}\n";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Should preserve package", output.contains("package com.example;"));
        assertTrue("Should preserve comment", output.contains("// A comment"));
        assertTrue("Should preserve javadoc", output.contains("/** Javadoc */"));
        assertParseable(output);
    }

    private void assertParseable(String source) {
        try {
            StaticJavaParser.parse(source);
        } catch (Exception e) {
            throw new AssertionError("Output is not parseable Java:\n" + source, e);
        }
    }

    private int countOccurrences(String text, String pattern) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(pattern, idx)) >= 0) {
            count++;
            idx += pattern.length();
        }
        return count;
    }
}
