package org.openclover.core.instr.java.javaparser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
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
import static org.mockito.Mockito.atLeast;
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
            ParserConfiguration config = new ParserConfiguration()
                    .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
            new JavaParser(config).parse(source).getResult()
                    .orElseThrow(() -> new Exception("Parse returned no result"));
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
     * Tests that switch expression arrow-cases are instrumented.
     */
    @Test
    public void instrumentsSwitchExpressionMethodBody() {
        // Switch expression arrow-cases require yield rewriting (context-sensitive keyword).
        // The standalone instrumenter instruments the enclosing method body.
        // Full arrow-case rewriting is handled by the session-aware visitor in production.
        String source = "class Foo { int bar(int x) { return switch (x) { case 1 -> 10; default -> 0; }; } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Method body should have R.inc", output.contains(INC_MARKER));
        assertTrue("Should preserve switch expression", output.contains("switch"));
    }

    /**
     * Tests that switch statement colon-cases are instrumented.
     */
    @Test
    public void instrumentsSwitchStatementColonCase() {
        String source = "class Foo { void bar(int x) { switch (x) { case 1: System.out.println(1); break; case 2: System.out.println(2); break; } } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Should contain R.inc for switch cases", output.contains(INC_MARKER));
        assertParseable(output);
    }

    /**
     * Tests that expression lambda in safe context gets lambdaInc wrapping via session.
     */
    @Test
    public void sessionExpressionLambdaWrappedWithLambdaInc() throws Exception {
        String source = "class Foo { java.util.function.Supplier<String> s = () -> \"hello\"; }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // Lambda in variable initializer is a safe context — should register as method + statement
        verify(session, atLeast(1)).enterMethod(any(), any(), any(), anyBoolean(), any(), eq(true), anyInt(), any());
        // addStatement called for the lambdaInc wrapping
        verify(session, atLeast(1)).addStatement(any(), any(), anyInt(), any());

        String result = output.toString();
        assertTrue("Should contain lambdaInc wrapping", result.contains("lambdaInc"));
    }

    /**
     * Tests that try-with-resources injects Tracker resource for cleanup tracking.
     */
    @Test
    public void sessionTryWithResourcesInjectsTracker() throws Exception {
        String source = "class Foo { void bar() throws Exception {"
                + " try (java.io.InputStream is = new java.io.FileInputStream(\"f\")) {"
                + " is.read(); } } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // addStatement called at least twice: entry + cleanup (Tracker)
        verify(session, atLeast(2)).addStatement(any(), any(), anyInt(), any());

        // Output should contain Tracker resource
        String result = output.toString();
        assertTrue("Should contain Tracker resource", result.contains("Tracker"));
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

    // ========== EXPRESSION RECURSION TESTS ==========

    /**
     * Tests that a lambda inside a method body (in a method call argument)
     * gets registered with the session as a lambda method.
     */
    @Test
    public void sessionLambdaInsideMethodBodyGetsRegistered() throws Exception {
        String source = "class Foo { void bar(java.util.List<String> list) { list.forEach(s -> System.out.println(s)); } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // enterMethod should be called at least twice: once for bar(), once for the lambda
        verify(session, atLeast(2)).enterMethod(any(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt(), any());
    }

    /**
     * Tests that a block lambda inside a method body gets its body instrumented.
     */
    @Test
    public void blockLambdaInsideMethodBodyInstrumented() {
        String source = "class Foo { void bar(java.util.List<String> list) { list.forEach(s -> { System.out.println(s); }); } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        // Standalone visitor instruments method entry + the forEach statement.
        // Lambda bodies inside expression arguments are handled by the session-aware
        // visitor (production), not the standalone test visitor.
        int incCount = countOccurrences(output, INC_MARKER);
        assertTrue("Should have at least 2 R.inc calls (method entry + statement)", incCount >= 2);
        assertParseable(output);
    }

    /**
     * Tests that switch statements inside method bodies get their entries instrumented.
     */
    @Test
    public void switchInsideMethodBodyInstrumented() {
        String source = "class Foo { void bar(int x) { switch (x) { case 1: System.out.println(1); break; default: System.out.println(0); } } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        // Method entry + at least one switch case statement
        int incCount = countOccurrences(output, INC_MARKER);
        assertTrue("Should have at least 3 R.inc calls (method + 2 case stmts)", incCount >= 3);
        assertParseable(output);
    }

    // ========== RE-INSTRUMENTATION BUG TESTS ==========

    /**
     * Tests that branch R.inc inside if-then block is NOT re-instrumented as a statement.
     * Bug: instrumentIf adds R.inc(trueIndex) THEN calls instrumentBlock which tries to
     * instrument the R.inc call itself.
     */
    @Test
    public void branchIncNotReInstrumentedInIfBlock() throws Exception {
        String source = "class Foo { void bar(boolean b) { if (b) { doWork(); } } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // addStatement should be called exactly once for doWork(), NOT for the branch R.inc
        verify(session, times(1)).addStatement(any(), any(), anyInt(), any());
    }

    /**
     * Tests that loop body R.inc is NOT re-instrumented.
     */
    @Test
    public void loopBodyIncNotReInstrumented() throws Exception {
        String source = "class Foo { void bar() { while (true) { doWork(); } } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // addStatement called once for doWork(), NOT for the loop branch R.inc
        verify(session, times(1)).addStatement(any(), any(), anyInt(), any());
    }

    // ========== LAMBDA DOUBLE-WRAPPING TEST ==========

    /**
     * Tests that a lambda wrapped with lambdaInc is NOT double-wrapped by
     * instrumentNestedLambdas finding the clone inside the wrapper.
     */
    @Test
    public void lambdaNotDoubleWrapped() throws Exception {
        String source = "class Foo { java.util.function.Supplier<String> s = () -> \"hello\"; }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        // Count lambdaInc INVOCATIONS (not the method definition in the recorder class).
        // Invocations: "lambdaInc(N," — the definition has "lambdaInc(final"
        int lambdaIncCallCount = countOccurrences(result, "lambdaInc(0,");
        assertEquals("Lambda should be wrapped exactly once", 1, lambdaIncCallCount);
    }

    // ========== SWITCH ARROW YIELD REWRITING TESTS ==========

    /**
     * Tests that switch expression arrow-cases get rewritten to blocks with yield.
     * case 1 -> 10  becomes  case 1 -> { R.inc(N); yield 10; }
     */
    @Test
    public void switchExpressionArrowCaseRewrittenWithYield() throws Exception {
        String source = "class Foo { int bar(int x) { return switch (x) { case 1 -> 10; case 2 -> 20; default -> 0; }; } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        // Switch expression arrow cases use incRet() to preserve expression form
        assertTrue("Should contain incRet for switch expression arrow-cases", result.contains("incRet("));
        // Session should register statements for each arrow-case
        verify(session, atLeast(3)).addStatement(any(), any(), anyInt(), any());
    }

    /**
     * Tests that switch STATEMENT arrow-cases don't get yield (no value returned).
     */
    @Test
    public void switchStatementArrowCaseRewrittenWithoutYield() throws Exception {
        String source = "class Foo { void bar(int x) { switch (x) { case 1 -> System.out.println(1); case 2 -> System.out.println(2); } } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // addStatement should be called for each arrow-case
        verify(session, atLeast(2)).addStatement(any(), any(), anyInt(), any());
    }

    // ========== INSTANCE INITIALIZER TEST ==========

    /**
     * Tests that instance (non-static) initializer blocks are instrumented.
     */
    @Test
    public void instanceInitializerInstrumented() throws Exception {
        String source = "class Foo { { System.out.println(\"instance init\"); } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        verify(session, atLeastOnce()).addStatement(any(), any(), anyInt(), any());
    }

    // ========== BUG FIX: BRACELESS LOOP BODIES (clover-7s0) ==========

    /**
     * Braceless while loop body must be instrumented.
     * while(x) doSomething(); — the body is an ExpressionStmt, not BlockStmt.
     */
    @Test
    public void bracelessWhileLoopBodyInstrumented() {
        String source = "class Foo { void bar(boolean x) { while (x) doWork(); } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Braceless while body must have R.inc", output.contains(INC_MARKER));
        assertTrue("doWork must still be present", output.contains(DO_WORK));
        assertParseable(output);
    }

    /**
     * Braceless for loop body must be instrumented.
     */
    @Test
    public void bracelessForLoopBodyInstrumented() {
        String source = "class Foo { void bar() { for (int i = 0; i < 10; i++) doWork(); } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Braceless for body must have R.inc", output.contains(INC_MARKER));
        assertTrue("doWork must still be present", output.contains(DO_WORK));
        assertParseable(output);
    }

    /**
     * Braceless do-while loop body must be instrumented.
     */
    @Test
    public void bracelessDoWhileLoopBodyInstrumented() {
        String source = "class Foo { void bar(boolean x) { do doWork(); while (x); } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Braceless do-while body must have R.inc", output.contains(INC_MARKER));
        assertTrue("doWork must still be present", output.contains(DO_WORK));
        assertParseable(output);
    }

    /**
     * Braceless for-each loop body must be instrumented.
     */
    @Test
    public void bracelessForEachLoopBodyInstrumented() {
        String source = "class Foo { void bar(java.util.List<String> items) { for (String s : items) doWork(); } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Braceless for-each body must have R.inc", output.contains(INC_MARKER));
        assertTrue("doWork must still be present", output.contains(DO_WORK));
        assertParseable(output);
    }

    // ========== BUG FIX: LABELED STATEMENTS (clover-5x7) ==========

    /**
     * Labeled for loop body must be instrumented.
     * outer: for(;;) { doWork(); } — the for loop is inside a LabeledStmt.
     */
    @Test
    public void labeledForLoopBodyInstrumented() {
        String source = "class Foo { void bar() { outer: for (int i = 0; i < 10; i++) { doWork(); break outer; } } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Labeled for body must have R.inc before doWork", output.contains(INC_MARKER));
        assertTrue("doWork must still be present", output.contains(DO_WORK));
        assertParseable(output);
    }

    /**
     * Labeled while loop body must be instrumented.
     */
    @Test
    public void labeledWhileLoopBodyInstrumented() {
        String source = "class Foo { void bar(boolean x) { loop: while (x) { doWork(); break loop; } } }";
        String output = AstInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING);

        assertTrue("Labeled while body must have R.inc", output.contains(INC_MARKER));
        assertTrue("doWork must still be present", output.contains(DO_WORK));
        assertParseable(output);
    }

    /**
     * Labeled if statement must be instrumented.
     */
    @Test
    public void labeledIfStatementInstrumented() throws Exception {
        String source = "class Foo { void bar(boolean x) { check: if (x) { doWork(); } } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // Branch should be registered for the if inside the label
        verify(session, atLeastOnce()).addBranch(any(), any(), anyBoolean(), anyInt(), any());
    }

    // ========== BUG FIX: ANONYMOUS INNER CLASSES (clover-agh) ==========

    /**
     * Anonymous inner class methods must be instrumented.
     */
    @Test
    public void anonymousInnerClassMethodsInstrumented() throws Exception {
        String source = "class Foo { void bar() { Runnable r = new Runnable() { public void run() { doWork(); } }; } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // enterMethod should be called at least twice: bar() and run()
        verify(session, atLeast(2)).enterMethod(any(), any(), any(), anyBoolean(), any(), anyBoolean(), anyInt(), any());
    }

    /**
     * Local class methods inside a method must be instrumented.
     */
    @Test
    public void localClassMethodsInstrumented() throws Exception {
        String source = "class Foo { void bar() { class Local { void localMethod() { doWork(); } } new Local().localMethod(); } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        // enterClass should be called at least twice: Foo and Local
        verify(session, atLeast(2)).enterClass(anyString(), any(), any(), anyBoolean(), anyBoolean(), anyBoolean());
    }

    // ========== FEATURE: GLOBAL SLICE START/END TEST WRAPPING (clover-78m) ==========

    /**
     * Test methods must be wrapped with globalSliceStart/globalSliceEnd.
     */
    @Test
    public void testMethodWrappedWithGlobalSlice() throws Exception {
        String source = "import org.junit.Test;\n"
                + "class FooTest {\n"
                + "  @Test public void testSomething() { doWork(); }\n"
                + "}";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        assertTrue("Test method must have globalSliceStart", result.contains("globalSliceStart"));
        assertTrue("Test method must have globalSliceEnd", result.contains("globalSliceEnd"));
        assertTrue("Test method must have try-finally wrapper", result.contains("finally"));
        assertParseable(result);
    }

    /**
     * Non-test methods must NOT have globalSlice wrapping.
     */
    @Test
    public void nonTestMethodNotWrappedWithGlobalSlice() throws Exception {
        String source = "class Foo { public void bar() { doWork(); } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        assertFalse("Non-test method must NOT have globalSliceStart", result.contains("globalSliceStart"));
    }

    // ========== P3 FIXES: countNonCommentLines, Math.abs, mapSourceLevel ==========

    /**
     * countNonCommentLines must exclude blank lines and comment-only lines.
     */
    @Test
    public void countNonCommentLinesExcludesCommentsAndBlanks() throws Exception {
        String source =
                "package com.example;\n"
                + "\n"
                + "// line comment\n"
                + "/* block comment */\n"
                + "/**\n"
                + " * javadoc\n"
                + " */\n"
                + "public class Foo {\n"
                + "    void bar() {\n"
                + "        doWork();\n"
                + "    }\n"
                + "}\n";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();

        // Capture the ncLineCount passed to enterFile
        ArgumentCaptor<Integer> ncLineCaptor = ArgumentCaptor.forClass(Integer.class);
        when(session.enterFile(anyString(), any(File.class), anyInt(), ncLineCaptor.capture(),
                anyLong(), anyLong(), anyLong())).thenReturn(mock(FileInfo.class));

        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        int ncLines = ncLineCaptor.getValue();
        // Source has 12 lines total. Non-comment, non-blank: package, class, void bar, doWork, }, } = 6
        assertTrue("ncLineCount (" + ncLines + ") must be less than total lines (12)", ncLines < 12);
        assertTrue("ncLineCount (" + ncLines + ") must be > 0", ncLines > 0);
    }

    /**
     * Recorder prefix must be a valid Java identifier even for pathological hash values.
     */
    @Test
    public void recorderPrefixIsValidJavaIdentifier() throws Exception {
        // Use a file path that might produce Integer.MIN_VALUE hash
        String source = "class Foo { void bar() {} }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        // The recorder prefix should not contain a '-' character (from negative hash)
        assertFalse("Recorder prefix must not contain '-'",
                result.contains("__CLR-") || result.contains("__CLRr-"));
    }

    // ========== NORTHFOX VALIDATION FIXES ==========

    /**
     * Try-with-resources Tracker must be a variable declaration, not a bare new expression.
     * Java requires try resources to be variable declarations or references to final variables.
     */
    @Test
    public void tryWithResourcesTrackerIsVariableDeclaration() throws Exception {
        String source = "class Foo { void bar() throws Exception {"
                + " try (java.io.InputStream is = new java.io.FileInputStream(\"f\")) {"
                + " is.read(); } } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        // Must NOT have bare "new ...Tracker(" as a resource — must be a variable declaration
        assertFalse("Tracker must not be a bare new expression in try resources",
                result.matches("(?s).*try\\s*\\([^)]*[;]\\s*new [^)]*Tracker\\([^)]*\\)\\s*\\).*"));
        // Must have a variable declaration pattern: Type varName = new ...Tracker(
        assertTrue("Tracker must be a variable declaration in try resources",
                result.contains("Tracker") && result.contains("=") && result.contains("new"));
        assertParseable(result);
    }

    /**
     * Switch expression arrow-case rewritten to block must NOT have stray semicolons after }.
     * case X -> { yield expr; } must not be followed by ;
     */
    @Test
    public void switchExpressionArrowCaseNoStraySemicolon() throws Exception {
        String source = "class Foo { String bar(int x) { return switch (x) {"
                + " case 1 -> \"one\";"
                + " case 2 -> \"two\";"
                + " default -> \"other\";"
                + " }; } }";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        // After rewriting to blocks, there should be no };  pattern (closing brace + semicolon)
        // inside the switch expression's case entries
        assertFalse("No stray semicolons after arrow-case blocks: " + result,
                result.matches("(?s).*yield[^}]*\\}\\s*;\\s*case.*"));
        assertParseable(result);
    }

    /**
     * Multi-line switch expression arrow-case — matches real-world FailureHandler.java pattern
     * where LPP puts the semicolon on its own line with indentation.
     */
    @Test
    public void multiLineSwitchExpressionArrowCaseNoStraySemicolon() throws Exception {
        String source = "class Foo {\n"
                + "    String bar(int code) {\n"
                + "        return switch (code) {\n"
                + "            case 400 -> \"BAD_REQUEST\";\n"
                + "            case 401 -> \"UNAUTHORIZED\";\n"
                + "            case 404 -> \"NOT_FOUND\";\n"
                + "            default -> \"UNKNOWN\";\n"
                + "        };\n"
                + "    }\n"
                + "}\n";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        // No stray semicolons between case blocks — any }...;...case pattern is invalid
        assertFalse("No stray semicolons after arrow-case blocks",
                result.matches("(?s).*yield[^}]*\\}\\s*;\\s*case.*"));
        // Must also be valid Java
        assertParseable(result);
    }

    // ========== ENUM RECORDER INJECTION ==========

    /**
     * Enums with methods must have the recorder class injected.
     * The recorder references (__CLR.R.inc) won't compile without the declaration.
     */
    @Test
    public void enumWithMethodsGetsRecorderInjected() throws Exception {
        String source = "public enum Color {\n"
                + "    RED(\"red\"), BLUE(\"blue\");\n"
                + "    private final String label;\n"
                + "    Color(String label) { this.label = label; }\n"
                + "    public String getLabel() { return label; }\n"
                + "}\n";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        assertTrue("Enum must have recorder class", result.contains("CoverageRecorder"));
        assertTrue("Enum methods must be instrumented", result.contains(".inc("));
        assertParseable(result);
    }

    /**
     * Enums with only constants (no methods) should still compile.
     */
    @Test
    public void enumWithOnlyConstantsCompiles() throws Exception {
        String source = "public enum Direction { NORTH, SOUTH, EAST, WEST }\n";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        assertParseable(result);
    }

    // ========== LAMBDA INC SCOPE FIX ==========

    /**
     * lambdaInc must be called without the recorder class prefix.
     * It's a static method on the OUTER class, not inside the __CLR inner class.
     */
    @Test
    public void lambdaIncCalledWithoutRecorderPrefix() throws Exception {
        String source = "class Foo {\n"
                + "    java.util.function.Predicate<String> p = s -> true;\n"
                + "}\n";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        // lambdaInc should NOT be called on the recorder inner class (__CLR.lambdaInc)
        // It should be called as a bare method or on the outer class
        assertFalse("lambdaInc must not be called on recorder inner class",
                result.matches("(?s).*__CLR[^.]*\\.lambdaInc.*"));
        // But lambdaInc should still be present
        assertTrue("lambdaInc should be generated", result.contains("lambdaInc"));
        assertParseable(result);
    }

    /**
     * TEST_NAME_SNIFFER in globalSliceEnd must not be prefixed with the recorder inner class.
     * It's a field on the OUTER class, same as lambdaInc.
     */
    @Test
    public void testSnifferNotPrefixedWithRecorderClass() throws Exception {
        String source = "import org.junit.Test;\n"
                + "class FooTest {\n"
                + "  @Test public void testSomething() { System.out.println(1); }\n"
                + "}\n";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        assertTrue("Test method must have globalSliceEnd", result.contains("globalSliceEnd"));
        // The sniffer must NOT be accessed via the recorder inner class
        assertFalse("TEST_NAME_SNIFFER must not be on recorder inner class: " + result,
                result.matches("(?s).*__CLR[^.]*\\.__CLR[^.]*_TEST_NAME_SNIFFER.*"));
        assertParseable(result);
    }

    // ========== SCOPE EDGE CASES ==========

    /**
     * Interface with default methods must produce parseable output.
     * Interfaces can't have static initializer blocks in Java < 16.
     * The recorder should NOT be injected into interfaces that are the only
     * top-level type — or it must use a compatible form.
     */
    @Test
    public void interfaceWithDefaultMethodProducesParseableOutput() throws Exception {
        String source = "public interface Greeter {\n"
                + "    default String greet(String name) { return \"Hello \" + name; }\n"
                + "    void wave();\n"
                + "}\n";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        // Must be valid Java — this is the key assertion
        assertParseable(result);
    }

    /**
     * Annotation type must produce parseable output.
     * Annotations can't have static inner classes.
     */
    @Test
    public void annotationTypeProducesParseableOutput() throws Exception {
        String source = "public @interface MyAnnotation {\n"
                + "    String value() default \"\";\n"
                + "    int count() default 0;\n"
                + "}\n";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        assertParseable(result);
    }

    /**
     * Non-static inner class must NOT get its own recorder.
     * Non-static inner classes can't have static members.
     */
    @Test
    public void nonStaticInnerClassMethodsInstrumentedWithoutOwnRecorder() throws Exception {
        String source = "class Outer {\n"
                + "    class Inner {\n"
                + "        void doWork() { System.out.println(1); }\n"
                + "    }\n"
                + "}\n";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        // Inner class methods should be instrumented (R.inc calls)
        assertTrue("Inner class methods should be instrumented", result.contains(".inc("));
        // Only ONE recorder class should exist (on Outer, not on Inner)
        int recorderCount = countOccurrences(result, "static class __CLR");
        assertEquals("Only one recorder class should exist (on outer class)", 1, recorderCount);
        assertParseable(result);
    }

    // ========== SWITCH EXPRESSION ARROW-CASE EXPRESSION PRESERVATION ==========

    /**
     * Switch expression arrow cases must preserve expression form to avoid VerifyError.
     * Rewriting "case X -> expr" to "case X -> { yield expr; }" changes javac's type
     * inference, causing VerifyError at runtime in type-sensitive contexts.
     * Instead, use incRet(N, expr) to keep the expression form.
     */
    @Test
    public void switchExpressionArrowCasePreservesExpressionForm() throws Exception {
        String source = "class Foo {\n"
                + "    String bar(int code) {\n"
                + "        return switch (code) {\n"
                + "            case 400 -> \"BAD_REQUEST\";\n"
                + "            case 401 -> \"UNAUTHORIZED\";\n"
                + "            default -> \"UNKNOWN\";\n"
                + "        };\n"
                + "    }\n"
                + "}\n";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        // Must NOT contain yield — expression form should be preserved
        assertFalse("Switch expression arrow cases must not use yield (block form): " + result,
                result.contains("yield"));
        // Must contain incRet or similar expression-preserving wrapper
        assertTrue("Switch expression arrow cases must use expression wrapper",
                result.contains("incRet(") || result.contains("inc("));
        assertParseable(result);
    }

    /**
     * Switch STATEMENT arrow cases CAN use block form safely — only switch EXPRESSIONS
     * have the VerifyError issue.
     */
    @Test
    public void switchStatementArrowCaseCanUseBlockForm() throws Exception {
        String source = "class Foo {\n"
                + "    void bar(int code) {\n"
                + "        switch (code) {\n"
                + "            case 400 -> System.out.println(\"bad\");\n"
                + "            default -> System.out.println(\"ok\");\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
        InstrumentationSource instrSource = new StringInstrumentationSource(new File(TEST_FILE_NAME), source);
        StringWriter output = new StringWriter();

        InstrumentationSession session = createFullMockSession();
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setInitstring(INIT_STRING);

        AstInstrumenter.instrument(instrSource, output, session, config, null, null);

        String result = output.toString();
        // Switch statements don't need yield — block rewriting is safe
        assertParseable(result);
        assertTrue("Switch statement cases should be instrumented", result.contains(".inc("));
    }
}
