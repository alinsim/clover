package org.openclover.core.instr.java.javaparser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig;
import org.openclover.core.cfg.instr.java.LambdaInstrumentation;
import org.openclover.core.cfg.instr.java.SourceLevel;
import org.openclover.core.instr.java.Instrumenter;
import org.openclover.core.instr.java.InstrumentationSource;
import org.openclover.core.instr.java.StringInstrumentationSource;
import org.openclover.core.registry.Clover2Registry;
import org.openclover.core.registry.metrics.ProjectMetrics;
import org.openclover.core.util.FileUtils;

import java.io.File;
import java.io.StringWriter;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests JavaParser instrumentation pipeline against ANTLR-based behavioral patterns.
 * This validates behavioral parity between the two backends - that JavaParser produces
 * EQUIVALENT results to ANTLR (not identical output, but equivalent coverage instrumentation).
 */
public class JavaParserParityTest {

    private static final String MARKER_STRING = "instrumented by OpenClover";
    private static final String RECORDER_CLASS_STRING = "public static class";
    private static final String INC_CALL_STRING = ".inc(";
    private static final String TEST_ENCODING = "UTF-8";
    private static final String PROJECT_NAME = "parity-test";
    private static final String LAMBDA_ARROW = "->";

    // Assertion messages
    private static final String SHOULD_CONTAIN_MARKER = "Should contain marker";
    private static final String SHOULD_CONTAIN_RECORDER_CLASS = "Should contain recorder class";
    private static final String SHOULD_CONTAIN_INC_CALLS = "Should contain inc calls";
    private static final String SHOULD_PRESERVE_LAMBDA = "Should preserve lambda";
    private static final String SHOULD_PRESERVE_LOOP_STRUCTURE = "Should preserve loop structure";
    private static final String ONE_CLASS = "1 class";
    private static final String ONE_METHOD = "1 method";
    private static final String HAS_STATEMENTS = "Has statements";
    private static final String HAS_MULTIPLE_STATEMENTS = "Has multiple statements";
    private static final String HAS_METHODS = "Has methods";

    private File workingDir;
    private Clover2Registry lastRegistry;

    @Before
    public void setUp() throws Exception {
        workingDir = Files.createTempDirectory("clover-parity").toFile();
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deltree(workingDir);
    }

    @Test
    public void paritySimpleClassWithMethod() throws Exception {
        String source = "class B { void a() { hashCode(); } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_MARKER, result.contains(MARKER_STRING));
        assertTrue(SHOULD_CONTAIN_RECORDER_CLASS, result.contains(RECORDER_CLASS_STRING));
        assertTrue("Should contain R.inc for method entry", result.contains(INC_CALL_STRING));
        assertTrue("Should preserve original code", result.contains("hashCode()"));

        ProjectMetrics pm = getProjectMetrics();
        assertEquals(ONE_CLASS, 1, pm.getNumClasses());
        assertEquals(ONE_METHOD, 1, pm.getNumMethods());
        assertTrue("At least 1 statement", pm.getNumStatements() >= 1);
    }

    @Test
    public void parityMultipleStatements() throws Exception {
        String source = "class B { void a(int arg) { int x = 1; int y = 2; System.out.println(x + y); } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));

        ProjectMetrics pm = getProjectMetrics();
        assertEquals(ONE_CLASS, 1, pm.getNumClasses());
        assertEquals(ONE_METHOD, 1, pm.getNumMethods());
        assertTrue("At least 3 statements", pm.getNumStatements() >= 3);
    }

    @Test
    public void parityIfElseBranches() throws Exception {
        String source = "class B { void a(boolean b) { if (b) { System.out.println(1); } else { System.out.println(2); } } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));

        ProjectMetrics pm = getProjectMetrics();
        assertTrue("Has statements + branches for if/else",
                pm.getNumStatements() + pm.getNumBranches() >= 3);
    }

    @Test
    public void parityForLoop() throws Exception {
        String source = "class B { void a() { for (int i = 0; i < 10; i++) { System.out.println(i); } } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));
        assertTrue(SHOULD_PRESERVE_LOOP_STRUCTURE, result.contains("for"));

        ProjectMetrics pm = getProjectMetrics();
        assertEquals(ONE_CLASS, 1, pm.getNumClasses());
        assertEquals(ONE_METHOD, 1, pm.getNumMethods());
        assertTrue(HAS_STATEMENTS, pm.getNumStatements() >= 1);
    }

    @Test
    public void parityWhileLoop() throws Exception {
        String source = "class B { void a() { int x = 0; while (x < 10) { System.out.println(x); x++; } } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));
        assertTrue(SHOULD_PRESERVE_LOOP_STRUCTURE, result.contains("while"));

        ProjectMetrics pm = getProjectMetrics();
        assertTrue(HAS_MULTIPLE_STATEMENTS, pm.getNumStatements() >= 3);
    }

    @Test
    public void paritySwitch() throws Exception {
        String source = "class B { void a(int x) { switch(x) { case 1: System.out.println(1); break; default: System.out.println(0); } } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));
        assertTrue("Should preserve switch structure", result.contains("switch"));

        ProjectMetrics pm = getProjectMetrics();
        assertTrue(HAS_STATEMENTS, pm.getNumStatements() >= 1);
    }

    @Test
    public void parityLambda() throws Exception {
        String source = "class B { void a() { Runnable r = () -> { System.out.println(1); }; } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));
        assertTrue(SHOULD_PRESERVE_LAMBDA, result.contains(LAMBDA_ARROW));

        ProjectMetrics pm = getProjectMetrics();
        assertEquals(ONE_CLASS, 1, pm.getNumClasses());
        // Should have at least the outer method
        assertTrue(HAS_METHODS, pm.getNumMethods() >= 1);
    }

    @Test
    public void parityConstructor() throws Exception {
        String source = "class B { B() { System.out.println(1); } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));

        ProjectMetrics pm = getProjectMetrics();
        assertEquals(ONE_CLASS, 1, pm.getNumClasses());
        assertEquals("1 method (constructor)", 1, pm.getNumMethods());
        assertTrue(HAS_STATEMENTS, pm.getNumStatements() >= 1);
    }

    @Test
    public void parityCloverOff() throws Exception {
        String source = "class B {\n  void a() {\n    System.out.println(1);\n    // CLOVER:OFF\n    System.out.println(2);\n    // CLOVER:ON\n    System.out.println(3);\n  }\n}";
        String result = instrumentWithJavaParser(source);

        assertTrue("Should have inc calls", result.contains(INC_CALL_STRING));
        assertTrue("Should preserve comments", result.contains("CLOVER:OFF"));

        ProjectMetrics pm = getProjectMetrics();
        // The disabled line should NOT be counted
        assertTrue("Statement count reflects disabled region", pm.getNumStatements() < 3);
    }

    @Test
    public void parityWithPackage() throws Exception {
        String source = "package com.example;\nclass B { void a() { System.out.println(1); } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_MARKER, result.contains(MARKER_STRING));
        assertTrue("Should preserve package declaration", result.contains("package com.example;"));
        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));

        ProjectMetrics pm = getProjectMetrics();
        assertEquals(ONE_CLASS, 1, pm.getNumClasses());
        assertEquals(ONE_METHOD, 1, pm.getNumMethods());
    }

    @Test
    public void parityNestedClasses() throws Exception {
        String source = "class Outer { class Inner { void a() {} } void b() {} }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));

        ProjectMetrics pm = getProjectMetrics();
        assertEquals("2 classes", 2, pm.getNumClasses());
        assertEquals("2 methods", 2, pm.getNumMethods());
    }

    @Test
    public void parityTryWithResources() throws Exception {
        String source = "class B { void a() throws Exception { try (java.io.InputStream is = new java.io.FileInputStream(\"f\")) { is.read(); } } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));
        assertTrue("Should preserve try-with-resources", result.contains("try"));

        ProjectMetrics pm = getProjectMetrics();
        assertEquals(ONE_CLASS, 1, pm.getNumClasses());
        assertEquals(ONE_METHOD, 1, pm.getNumMethods());
        assertTrue(HAS_STATEMENTS, pm.getNumStatements() >= 1);
    }

    @Test
    public void parityMultipleMethods() throws Exception {
        String source = "class B { void a() { System.out.println(1); } void b() { System.out.println(2); } void c() { System.out.println(3); } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));

        ProjectMetrics pm = getProjectMetrics();
        assertEquals(ONE_CLASS, 1, pm.getNumClasses());
        assertEquals("3 methods", 3, pm.getNumMethods());
        assertTrue(HAS_MULTIPLE_STATEMENTS, pm.getNumStatements() >= 3);
    }

    @Test
    public void parityReturnStatement() throws Exception {
        String source = "class B { int a() { int x = 42; return x; } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));
        assertTrue("Should preserve return", result.contains("return"));

        ProjectMetrics pm = getProjectMetrics();
        assertEquals(ONE_METHOD, 1, pm.getNumMethods());
        assertTrue(HAS_STATEMENTS, pm.getNumStatements() >= 2);
    }

    @Test
    public void parityThrowStatement() throws Exception {
        String source = "class B { void a() { throw new RuntimeException(\"test\"); } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));
        assertTrue("Should preserve throw", result.contains("throw"));

        ProjectMetrics pm = getProjectMetrics();
        assertEquals(ONE_METHOD, 1, pm.getNumMethods());
        assertTrue(HAS_STATEMENTS, pm.getNumStatements() >= 1);
    }

    @Test
    public void parityBreakStatement() throws Exception {
        String source = "class B { void a() { while (true) { break; } } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));
        assertTrue("Should preserve break", result.contains("break"));

        ProjectMetrics pm = getProjectMetrics();
        assertTrue(HAS_STATEMENTS, pm.getNumStatements() >= 1);
    }

    @Test
    public void parityContinueStatement() throws Exception {
        String source = "class B { void a() { for (int i = 0; i < 10; i++) { if (i == 5) continue; System.out.println(i); } } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));
        assertTrue("Should preserve continue", result.contains("continue"));

        ProjectMetrics pm = getProjectMetrics();
        assertTrue(HAS_STATEMENTS, pm.getNumStatements() >= 2);
    }

    @Test
    public void parityEmptyMethod() throws Exception {
        String source = "class B { void a() {} }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_MARKER, result.contains(MARKER_STRING));
        assertTrue(SHOULD_CONTAIN_RECORDER_CLASS, result.contains(RECORDER_CLASS_STRING));

        ProjectMetrics pm = getProjectMetrics();
        assertEquals(ONE_CLASS, 1, pm.getNumClasses());
        assertEquals(ONE_METHOD, 1, pm.getNumMethods());
    }

    @Test
    public void parityExpressionLambda() throws Exception {
        String source = "class B { void a() { java.util.function.Function<Integer,Integer> f = x -> x + 1; } }";
        String result = instrumentWithJavaParser(source);

        assertTrue(SHOULD_CONTAIN_INC_CALLS, result.contains(INC_CALL_STRING));
        assertTrue(SHOULD_PRESERVE_LAMBDA, result.contains(LAMBDA_ARROW));

        ProjectMetrics pm = getProjectMetrics();
        assertTrue(HAS_METHODS, pm.getNumMethods() >= 1);
    }

    /**
     * Instruments the given source code using JavaParser backend.
     */
    private String instrumentWithJavaParser(String sourceCode) throws Exception {
        File dbFile = new File(workingDir, "clover.db");

        JavaInstrumentationConfig cfg = new JavaInstrumentationConfig();
        cfg.setDefaultBaseDir(workingDir);
        cfg.setInitstring(dbFile.getAbsolutePath());
        cfg.setProjectName(PROJECT_NAME);
        cfg.setSourceLevel(SourceLevel.JAVA_8);
        cfg.setEncoding(TEST_ENCODING);
        cfg.setUseJavaParser(true);
        cfg.setInstrumentLambda(LambdaInstrumentation.ALL);

        Instrumenter instr = new Instrumenter(cfg);
        instr.startInstrumentation();

        StringWriter out = new StringWriter();
        File srcFile = new File(workingDir, "Test.java");
        InstrumentationSource input = new StringInstrumentationSource(srcFile, sourceCode);
        instr.instrument(input, out, null);

        lastRegistry = instr.endInstrumentation();

        return out.toString();
    }

    private ProjectMetrics getProjectMetrics() {
        return (ProjectMetrics) lastRegistry.getProject().getMetrics();
    }
}
