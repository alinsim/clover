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

        assertTrue("Should contain inc(0)", result.contains(INC_PREFIX + "0)"));
        assertTrue("Should contain inc(1)", result.contains(INC_PREFIX + "1)"));
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
}
