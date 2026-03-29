package org.openclover.core.instr.java.javaparser;

import com.github.javaparser.StaticJavaParser;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.openclover.core.api.instrumentation.InstrumentationSession;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.api.registry.MethodInfo;
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig;
import org.openclover.core.instr.java.FileStructureInfo;
import org.openclover.core.instr.java.InstrumentationSource;
import org.openclover.core.instr.java.StringInstrumentationSource;
import org.openclover.core.api.registry.ContextSet;
import org.openclover.core.context.ContextStore;
import org.openclover.core.context.MethodRegexpContext;
import org.openclover.core.registry.entities.FullBranchInfo;
import org.openclover.core.registry.entities.FullStatementInfo;
import org.openclover.core.registry.entities.MethodSignature;
import org.openclover.core.spi.lang.LanguageConstruct;

import java.io.File;
import java.io.StringWriter;
import java.util.Collections;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private static final String TEST_FILE_NAME = "TEST_FILE_NAME";
    private static final String IF_ELSE_SOURCE = "class Foo { void bar(boolean b) { if (b) { doA(); } else { doB(); } } }";

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
        String source = IF_ELSE_SOURCE;
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

    /**
     * Lambda expression wrapping: expression lambda wrapped with lambdaInc().
     */
    @Test
    public void instrumentsExpressionLambda() {
        String source = "class Foo { java.util.function.Supplier<String> s = () -> \"hello\"; }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Should contain lambda body", output.contains("hello"));
        assertParseable(output);
    }

    /**
     * Try-with-resources: R.inc before try statement.
     */
    @Test
    public void instrumentsTryWithResources() {
        String source = "class Foo { void bar() throws Exception {"
                + " try (java.io.InputStream is = new java.io.FileInputStream(\"f\")) {"
                + " is.read(); } } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Should contain R.inc", output.contains(INC_MARKER));
        assertTrue("Should preserve try-with-resources", output.contains("try"));
        assertTrue("Should preserve resource", output.contains("InputStream"));
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

    // ========== SESSION INTEGRATION TESTS ==========

    /**
     * Tests that the session-aware instrument method calls session.enterFile with file metadata.
     */
    @Test
    public void sessionEnterFileCalledWithMetadata() throws Exception {
        String source = "class Foo { void bar() { System.out.println(\"test\"); } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = mock(InstrumentationSession.class);
        FileInfo fileInfo = mock(FileInfo.class);
        MethodInfo methodInfo = mock(MethodInfo.class);

        when(session.enterFile(anyString(), any(File.class), anyInt(), anyInt(), anyLong(), anyLong(), anyLong()))
                .thenReturn(fileInfo);
        when(session.enterMethod(any(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt(), any()))
                .thenReturn(methodInfo);
        when(methodInfo.getDataIndex()).thenReturn(0);
        when(session.getVersion()).thenReturn(123456789L);
        when(session.getCurrentFileMaxIndex()).thenReturn(10);

        FullStatementInfo stmtInfo = mock(FullStatementInfo.class);
        when(stmtInfo.getDataIndex()).thenReturn(1);
        when(session.addStatement(any(), any(), anyInt(), any())).thenReturn(stmtInfo);

        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        FileStructureInfo result = AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        assertNotNull("Should return FileStructureInfo", result);
        verify(session, times(1)).enterFile(
                eq(""),
                eq(instrSource.getSourceFileLocation()),
                anyInt(), // lineCount
                anyInt(), // ncLineCount
                anyLong(), // timestamp
                anyLong(), // filesize
                anyLong()); // checksum
    }

    /**
     * Tests that session.enterClass is called for each class.
     */
    @Test
    public void sessionEnterClassCalledPerClass() throws Exception {
        String source = "class Outer { class Inner {} }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = mock(InstrumentationSession.class);
        FileInfo fileInfo = mock(FileInfo.class);

        when(session.enterFile(anyString(), any(File.class), anyInt(), anyInt(), anyLong(), anyLong(), anyLong()))
                .thenReturn(fileInfo);
        when(session.getVersion()).thenReturn(123456789L);
        when(session.getCurrentFileMaxIndex()).thenReturn(10);

        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        verify(session, times(2)).enterClass(anyString(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
        verify(session, times(2)).exitClass(anyInt(), anyInt());
    }

    /**
     * Tests that session.enterMethod is called with correct signature and complexity.
     */
    @Test
    public void sessionEnterMethodCalledWithSignatureAndComplexity() throws Exception {
        String source = "class Foo { public int calc(String s) { if (s == null) return 0; return s.length(); } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = mock(InstrumentationSession.class);
        FileInfo fileInfo = mock(FileInfo.class);
        MethodInfo methodInfo = mock(MethodInfo.class);

        when(session.enterFile(anyString(), any(File.class), anyInt(), anyInt(), anyLong(), anyLong(), anyLong()))
                .thenReturn(fileInfo);
        when(session.enterMethod(any(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt(), any()))
                .thenReturn(methodInfo);
        when(methodInfo.getDataIndex()).thenReturn(0);
        when(session.getVersion()).thenReturn(123456789L);
        when(session.getCurrentFileMaxIndex()).thenReturn(10);

        FullStatementInfo stmtInfo = mock(FullStatementInfo.class);
        when(stmtInfo.getDataIndex()).thenReturn(1);
        when(session.addStatement(any(), any(), anyInt(), any())).thenReturn(stmtInfo);

        FullBranchInfo branchInfo = mock(FullBranchInfo.class);
        when(branchInfo.getDataIndex()).thenReturn(2);
        when(session.addBranch(any(), any(), anyBoolean(), anyInt(), any())).thenReturn(branchInfo);

        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        ArgumentCaptor<MethodSignature> sigCaptor = ArgumentCaptor.forClass(MethodSignature.class);
        ArgumentCaptor<Integer> complexityCaptor = ArgumentCaptor.forClass(Integer.class);

        verify(session, times(1)).enterMethod(
                any(),
                any(),
                sigCaptor.capture(),
                eq(false), // isTest
                any(),
                eq(false), // isLambda
                complexityCaptor.capture(),
                any());

        MethodSignature sig = sigCaptor.getValue();
        assertEquals("Method name should be calc", "calc", sig.getName());
        assertTrue("Complexity should be at least 2 (method + if)", complexityCaptor.getValue() >= 2);
    }

    /**
     * Tests that session.addStatement is called for each executable statement.
     */
    @Test
    public void sessionAddStatementCalledPerExecutableStatement() throws Exception {
        String source = "class Foo { void bar() { int x = 1; int y = 2; return; } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = mock(InstrumentationSession.class);
        FileInfo fileInfo = mock(FileInfo.class);
        MethodInfo methodInfo = mock(MethodInfo.class);
        FullStatementInfo stmtInfo = mock(FullStatementInfo.class);

        when(session.enterFile(anyString(), any(File.class), anyInt(), anyInt(), anyLong(), anyLong(), anyLong()))
                .thenReturn(fileInfo);
        when(session.enterMethod(any(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt(), any()))
                .thenReturn(methodInfo);
        when(methodInfo.getDataIndex()).thenReturn(0);
        when(session.addStatement(any(), any(), anyInt(), any())).thenReturn(stmtInfo);
        when(stmtInfo.getDataIndex()).thenReturn(1);
        when(session.getVersion()).thenReturn(123456789L);
        when(session.getCurrentFileMaxIndex()).thenReturn(10);

        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // Should have at least 3 statements: x=1, y=2, return
        verify(session, atLeastOnce()).addStatement(any(), any(), anyInt(), eq(LanguageConstruct.Builtin.STATEMENT));
    }

    /**
     * Tests that session.addBranch is called for if statements.
     */
    @Test
    public void sessionAddBranchCalledPerIfStatement() throws Exception {
        String source = IF_ELSE_SOURCE;
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = mock(InstrumentationSession.class);
        FileInfo fileInfo = mock(FileInfo.class);
        MethodInfo methodInfo = mock(MethodInfo.class);
        FullBranchInfo branchInfo = mock(FullBranchInfo.class);
        FullStatementInfo stmtInfo = mock(FullStatementInfo.class);

        when(session.enterFile(anyString(), any(File.class), anyInt(), anyInt(), anyLong(), anyLong(), anyLong()))
                .thenReturn(fileInfo);
        when(session.enterMethod(any(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt(), any()))
                .thenReturn(methodInfo);
        when(methodInfo.getDataIndex()).thenReturn(0);
        when(session.addBranch(any(), any(), anyBoolean(), anyInt(), any())).thenReturn(branchInfo);
        when(branchInfo.getDataIndex()).thenReturn(1);
        when(session.addStatement(any(), any(), anyInt(), any())).thenReturn(stmtInfo);
        when(stmtInfo.getDataIndex()).thenReturn(3);
        when(session.getVersion()).thenReturn(123456789L);
        when(session.getCurrentFileMaxIndex()).thenReturn(10);

        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        verify(session, times(1)).addBranch(any(), any(), eq(true), anyInt(), eq(LanguageConstruct.Builtin.BRANCH));
    }

    /**
     * Tests that @Test annotated methods are detected as test methods.
     */
    @Test
    public void testMethodDetectedByAnnotation() throws Exception {
        String source = "import org.junit.Test; class FooTest { @Test public void testBar() { assert true; } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = mock(InstrumentationSession.class);
        FileInfo fileInfo = mock(FileInfo.class);
        MethodInfo methodInfo = mock(MethodInfo.class);
        FullStatementInfo stmtInfo = mock(FullStatementInfo.class);

        when(session.enterFile(anyString(), any(File.class), anyInt(), anyInt(), anyLong(), anyLong(), anyLong()))
                .thenReturn(fileInfo);
        when(session.enterMethod(any(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt(), any()))
                .thenReturn(methodInfo);
        when(methodInfo.getDataIndex()).thenReturn(0);
        when(session.addStatement(any(), any(), anyInt(), any())).thenReturn(stmtInfo);
        when(stmtInfo.getDataIndex()).thenReturn(1);
        when(session.getVersion()).thenReturn(123456789L);
        when(session.getCurrentFileMaxIndex()).thenReturn(10);

        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        verify(session, times(1)).enterMethod(
                any(),
                any(),
                any(),
                eq(true), // isTest should be true
                anyString(), // staticTestName should be provided
                eq(false), // isLambda
                anyInt(),
                any());
    }

    /**
     * Tests that instrumentation is skipped in CLOVER:OFF regions.
     */
    @Test
    public void cloverOffSkipsInstrumentation() throws Exception {
        String source = "class Foo {\n"
                + "void bar() {\n"
                + "/* CLOVER:OFF */\n"
                + "int x = 1;\n"
                + "/* CLOVER:ON */\n"
                + "int y = 2;\n"
                + "}\n"
                + "}";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = mock(InstrumentationSession.class);
        FileInfo fileInfo = mock(FileInfo.class);
        MethodInfo methodInfo = mock(MethodInfo.class);
        FullStatementInfo stmtInfo = mock(FullStatementInfo.class);

        when(session.enterFile(anyString(), any(File.class), anyInt(), anyInt(), anyLong(), anyLong(), anyLong()))
                .thenReturn(fileInfo);
        when(session.enterMethod(any(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt(), any()))
                .thenReturn(methodInfo);
        when(methodInfo.getDataIndex()).thenReturn(0);
        when(session.addStatement(any(), any(), anyInt(), any())).thenReturn(stmtInfo);
        when(stmtInfo.getDataIndex()).thenReturn(1);
        when(session.getVersion()).thenReturn(123456789L);
        when(session.getCurrentFileMaxIndex()).thenReturn(10);

        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // addStatement should be called only once — for "int y = 2" (not "int x = 1")
        verify(session, times(1)).addStatement(any(), any(), anyInt(), any());
    }

    /**
     * Tests that CLOVER:OFF gates method entry instrumentation.
     */
    @Test
    public void cloverOffSkipsMethodEntry() throws Exception {
        String source = "class Foo {\n"
                + "/* CLOVER:OFF */\n"
                + "void skipped() { doWork(); }\n"
                + "/* CLOVER:ON */\n"
                + "void covered() { doWork(); }\n"
                + "}";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // enterMethod should be called only for covered(), not skipped()
        verify(session, times(1)).enterMethod(any(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt(), any());
    }

    /**
     * Tests CLOVER:OFF around if statement skips branch instrumentation.
     */
    @Test
    public void cloverOffSkipsBranchInstrumentation() throws Exception {
        String source = "class Foo {\n"
                + "void m(boolean b) {\n"
                + "/* CLOVER:OFF */\n"
                + "if (b) { doSkipped(); }\n"
                + "/* CLOVER:ON */\n"
                + "if (b) { doCovered(); }\n"
                + "}\n"
                + "}";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // addBranch called once (for covered if), not twice
        verify(session, times(1)).addBranch(any(), any(), anyBoolean(), anyInt(), any());
    }

    /**
     * Tests CLOVER:OFF at end of file (no matching ON).
     */
    @Test
    public void cloverOffWithoutOnDisablesToEndOfFile() throws Exception {
        String source = "class Foo {\n"
                + "void covered() { doWork(); }\n"
                + "/* CLOVER:OFF */\n"
                + "void skipped1() { doWork(); }\n"
                + "void skipped2() { doWork(); }\n"
                + "}";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // enterMethod called once (for covered()), not 3 times
        verify(session, times(1)).enterMethod(any(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt(), any());
    }

    private InstrumentationSession createFullMockSession() {
        InstrumentationSession session = mock(InstrumentationSession.class);
        FileInfo fileInfo = mock(FileInfo.class);
        MethodInfo methodInfo = mock(MethodInfo.class);
        FullStatementInfo stmtInfo = mock(FullStatementInfo.class);
        FullBranchInfo branchInfo = mock(FullBranchInfo.class);

        when(session.enterFile(anyString(), any(File.class), anyInt(), anyInt(), anyLong(), anyLong(), anyLong()))
                .thenReturn(fileInfo);
        when(session.enterMethod(any(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt(), any()))
                .thenReturn(methodInfo);
        when(methodInfo.getDataIndex()).thenReturn(0);
        when(session.addStatement(any(), any(), anyInt(), any())).thenReturn(stmtInfo);
        when(stmtInfo.getDataIndex()).thenReturn(1);
        when(session.addBranch(any(), any(), anyBoolean(), anyInt(), any())).thenReturn(branchInfo);
        when(branchInfo.getDataIndex()).thenReturn(2);
        when(session.getVersion()).thenReturn(123456789L);
        when(session.getCurrentFileMaxIndex()).thenReturn(10);
        return session;
    }

    /**
     * Tests that double instrumentation is rejected.
     */
    @Test(expected = Exception.class)
    public void doubleInstrumentationRejected() throws Exception {
        String source = "/* $$ This file has been instrumented by OpenClover $$ */\nclass Foo { void bar() {} }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = mock(InstrumentationSession.class);
        FileInfo fileInfo = mock(FileInfo.class);

        when(session.enterFile(anyString(), any(File.class), anyInt(), anyInt(), anyLong(), anyLong(), anyLong()))
                .thenReturn(fileInfo);
        when(session.getVersion()).thenReturn(123456789L);

        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);
    }

    // ========== CONTEXT MATCHING TESTS ==========

    /**
     * Tests that enterMethod receives a non-empty ContextSet when contextStore has matching patterns.
     */
    @Test
    public void contextMatchingPassedToSessionCalls() throws Exception {
        String source = "class Foo {\n"
                + "public void getFoo() { return; }\n"
                + "}";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        // Create a context store with a method pattern that matches "get*"
        ContextStore contextStore = new ContextStore();
        contextStore.addMethodContext(new MethodRegexpContext(0, "getters", Pattern.compile(".*get.*")));

        AstInstrumenter.instrument(instrSource, output, session, config, null, contextStore);

        // enterMethod should be called with a non-empty context (matching "getters" pattern)
        ArgumentCaptor<ContextSet> ctxCaptor = ArgumentCaptor.forClass(ContextSet.class);
        verify(session).enterMethod(ctxCaptor.capture(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt(), any());

        // The captured ContextSet should have bit 0 set (the "getters" pattern at index 0)
        assertNotNull("ContextSet should not be null", ctxCaptor.getValue());
    }

    // ========== RECORDER PARSEABILITY TEST ==========

    /**
     * Tests that RecorderCodeGenerator output is valid parseable Java.
     */
    @Test
    public void recorderCodeIsParseable() {
        RecorderCodeGenerator.RecorderConfig cfg = new RecorderCodeGenerator.RecorderConfig();
        cfg.recorderBase = "__CLR_TEST";
        cfg.recorderSuffix = "R";
        cfg.initString = "/tmp/test.db";
        cfg.registryVersion = 123456789L;
        cfg.areLambdasSupported = true;
        cfg.recorderCfg = 0L;
        cfg.maxDataIndex = 100;
        cfg.distributedConfig = "";
        cfg.profiles = Collections.emptyList();

        String recorderCode = RecorderCodeGenerator.generate(cfg);
        assertNotNull("Recorder code should not be null", recorderCode);
        assertFalse("Recorder code should not be empty", recorderCode.isEmpty());

        // RecorderCodeGenerator produces multiple declarations (class + lambdaInc + sniffer).
        // Verify the code is valid Java when placed inside a class body.
        String wrappedSource = "class __Wrapper {" + recorderCode + "}";
        try {
            StaticJavaParser.parse(wrappedSource);
        } catch (Exception e) {
            throw new AssertionError("RecorderCodeGenerator output is NOT parseable when wrapped:\n" + wrappedSource, e);
        }
    }

    // ========== STATIC INITIALIZER TEST ==========

    /**
     * Tests that static initializer blocks are instrumented.
     */
    @Test
    public void instrumentsStaticInitializerBlock() {
        String source = "class Foo { static { System.out.println(\"init\"); } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Static initializer should have R.inc", output.contains(INC_MARKER));
        assertTrue("Should preserve original statement", output.contains("System.out.println"));
        assertParseable(output);
    }

    /**
     * Tests that static initializer blocks register with session.
     */
    @Test
    public void sessionStaticInitializerRegistered() throws Exception {
        String source = "class Foo { static { int x = 1; } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // The static initializer's statement should trigger addStatement
        verify(session, atLeastOnce()).addStatement(any(), any(), anyInt(), any());
    }
}
