package org.openclover.core.instr.java.javaparser;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openclover.core.api.instrumentation.InstrumentationSession;
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig;
import org.openclover.core.cfg.instr.java.SourceLevel;
import org.openclover.core.instr.java.FileStructureInfo;
import org.openclover.core.instr.java.StringInstrumentationSource;
import org.openclover.core.registry.Clover2Registry;
import org.openclover.core.registry.metrics.ProjectMetrics;
import org.openclover.core.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests for SessionAwareInstrumenter that verify integration with the InstrumentationSession lifecycle.
 */
public class SessionAwareInstrumenterTest {

    private static final String RECORDER_CLASS_PATTERN = "public static class __CLR";
    private static final String INC_CALL_PATTERN = ".inc(";
    private static final String ASSERT_ONE_CLASS = "Should have 1 class";
    private static final String ASSERT_ONE_METHOD = "Should have 1 method";
    private static final String ASSERT_MULTIPLE_STATEMENTS = "Should have multiple statements";
    private static final String ASSERT_FILE_STRUCTURE_NOT_NULL = "FileStructureInfo should not be null";
    private static final String ASSERT_INSTRUMENTED_NOT_NULL = "Instrumented output should not be null";
    private static final String ASSERT_CONTAINS_MARKER = "Should contain instrumentation marker";
    private static final String MARKER_TEXT = "This file has been instrumented by OpenClover";
    private static final String ASSERT_AT_LEAST_ONE_STATEMENT = "Should have at least 1 statement";
    private static final String METHOD_CLOSING = "    }\n";

    private File workingDir;
    private File registryFile;

    @Before
    public void setUp() throws Exception {
        workingDir = File.createTempFile(getClass().getName(), ".tmp");
        workingDir.delete();
        workingDir.mkdir();

        registryFile = new File(workingDir, "clover.db");
    }

    @After
    public void tearDown() throws Exception {
        FileUtils.deltree(workingDir);
    }

    @Test
    public void testInstrumentSimpleClass() throws Exception {
        String sourceCode = "public class HelloWorld { public void sayHello() { System.out.println(\"Hello\"); } }";

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "HelloWorld.java"), sourceCode);

        FileStructureInfo structureInfo = SessionAwareInstrumenter.instrument(
                source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        assertNotNull(ASSERT_FILE_STRUCTURE_NOT_NULL, structureInfo);

        String instrumented = output.toString();
        assertNotNull(ASSERT_INSTRUMENTED_NOT_NULL, instrumented);
        assertTrue(ASSERT_CONTAINS_MARKER, instrumented.contains(MARKER_TEXT));
        assertTrue("Should contain recorder class", instrumented.contains(RECORDER_CLASS_PATTERN));
        assertTrue("Should contain R.inc() calls", instrumented.contains(INC_CALL_PATTERN));

        ProjectMetrics metrics = (ProjectMetrics) registry.getProject().getMetrics();
        assertEquals(ASSERT_ONE_CLASS, 1, metrics.getNumClasses());
        assertEquals(ASSERT_ONE_METHOD, 1, metrics.getNumMethods());
        assertTrue(ASSERT_AT_LEAST_ONE_STATEMENT, metrics.getNumStatements() >= 1);
    }

    @Test
    public void testInstrumentMultipleMethods() throws Exception {
        String sourceCode =
                "public class Calculator {\n" +
                "    public int add(int a, int b) {\n" +
                "        return a + b;\n" +
                METHOD_CLOSING +
                "    public int subtract(int a, int b) {\n" +
                "        return a - b;\n" +
                METHOD_CLOSING +
                "}";

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "Calculator.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        ProjectMetrics metrics = (ProjectMetrics) registry.getProject().getMetrics();
        assertEquals(ASSERT_ONE_CLASS, 1, metrics.getNumClasses());
        assertEquals("Should have 2 methods", 2, metrics.getNumMethods());
        assertTrue(ASSERT_MULTIPLE_STATEMENTS, metrics.getNumStatements() >= 2);
    }

    @Test
    public void testInstrumentWithConstructor() throws Exception {
        String sourceCode =
                "public class Person {\n" +
                "    private String name;\n" +
                "    public Person(String name) {\n" +
                "        this.name = name;\n" +
                METHOD_CLOSING +
                "    public String getName() {\n" +
                "        return name;\n" +
                METHOD_CLOSING +
                "}";

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "Person.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        ProjectMetrics metrics = (ProjectMetrics) registry.getProject().getMetrics();
        assertEquals(ASSERT_ONE_CLASS, 1, metrics.getNumClasses());
        assertEquals("Should have 2 methods (constructor + getter)", 2, metrics.getNumMethods());
        assertTrue(ASSERT_MULTIPLE_STATEMENTS, metrics.getNumStatements() >= 2);
    }

    @Test
    public void testInstrumentWithBranches() throws Exception {
        String sourceCode =
                "public class Conditional {\n" +
                "    public String classify(int value) {\n" +
                "        if (value > 0) {\n" +
                "            return \"positive\";\n" +
                "        } else {\n" +
                "            return \"non-positive\";\n" +
                "        }\n" +
                METHOD_CLOSING +
                "}";

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "Conditional.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();
        assertTrue("Should contain multiple R.inc() calls for branches", instrumented.contains(INC_CALL_PATTERN));

        ProjectMetrics metrics = (ProjectMetrics) registry.getProject().getMetrics();
        assertEquals(ASSERT_ONE_CLASS, 1, metrics.getNumClasses());
        assertEquals(ASSERT_ONE_METHOD, 1, metrics.getNumMethods());
        assertTrue("Should have statements for branches", metrics.getNumStatements() >= 3);
    }

    @Test
    public void testInstrumentWithPackage() throws Exception {
        String sourceCode =
                "package com.example.test;\n" +
                "public class Example {\n" +
                "    public void doSomething() {\n" +
                "        System.out.println(\"test\");\n" +
                METHOD_CLOSING +
                "}";

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "Example.java"), sourceCode);

        FileStructureInfo structureInfo = SessionAwareInstrumenter.instrument(
                source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        assertEquals("Package name should be extracted", "com.example.test", structureInfo.getPackageName());

        ProjectMetrics metrics = (ProjectMetrics) registry.getProject().getMetrics();
        assertEquals(ASSERT_ONE_CLASS, 1, metrics.getNumClasses());
    }

    @Test
    public void testInstrumentWithCloverOff() throws Exception {
        String sourceCode =
                "public class WithCloverOff {\n" +
                "    public void method1() {\n" +
                "        System.out.println(\"instrumented\");\n" +
                METHOD_CLOSING +
                "    // CLOVER:OFF\n" +
                "    public void method2() {\n" +
                "        System.out.println(\"not instrumented\");\n" +
                METHOD_CLOSING +
                "    // CLOVER:ON\n" +
                "    public void method3() {\n" +
                "        System.out.println(\"instrumented again\");\n" +
                METHOD_CLOSING +
                "}";

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "WithCloverOff.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        ProjectMetrics metrics = (ProjectMetrics) registry.getProject().getMetrics();
        assertEquals(ASSERT_ONE_CLASS, 1, metrics.getNumClasses());
        assertEquals("Should have 3 methods", 3, metrics.getNumMethods());
    }

    @Test
    public void testInstrumentEmptyClass() throws Exception {
        String sourceCode = "public class EmptyClass { }";

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "EmptyClass.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();
        assertTrue("Should contain recorder class even in empty class", instrumented.contains(RECORDER_CLASS_PATTERN));

        ProjectMetrics metrics = (ProjectMetrics) registry.getProject().getMetrics();
        assertEquals(ASSERT_ONE_CLASS, 1, metrics.getNumClasses());
        assertEquals("Should have 0 methods", 0, metrics.getNumMethods());
    }

    @Test
    public void testInstrumentInterfaceWithDefaultMethods() throws Exception {
        String sourceCode =
                "import java.util.Iterator;\n" +
                "public interface TestInterface extends Iterator {\n" +
                "    default boolean isLast() {\n" +
                "        return !hasNext();\n" +
                METHOD_CLOSING +
                "    void forwardToLast();\n" +
                "}";

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "TestInterface.java"), sourceCode);

        FileStructureInfo structureInfo = SessionAwareInstrumenter.instrument(
                source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        assertNotNull(ASSERT_FILE_STRUCTURE_NOT_NULL, structureInfo);

        String instrumented = output.toString();
        assertNotNull(ASSERT_INSTRUMENTED_NOT_NULL, instrumented);
        assertTrue(ASSERT_CONTAINS_MARKER, instrumented.contains(MARKER_TEXT));
        assertTrue("Should contain R.inc() calls for default method", instrumented.contains(INC_CALL_PATTERN));

        ProjectMetrics metrics = (ProjectMetrics) registry.getProject().getMetrics();
        assertEquals("Should have 1 interface", 1, metrics.getNumClasses());
        assertEquals("Should have 1 default method", 1, metrics.getNumMethods());
        assertTrue(ASSERT_AT_LEAST_ONE_STATEMENT, metrics.getNumStatements() >= 1);
    }

    /**
     * Creates a test configuration for instrumentation.
     */
    private JavaInstrumentationConfig createConfig() throws IOException {
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setDefaultBaseDir(workingDir);
        config.setInitstring(registryFile.getAbsolutePath());
        config.setProjectName("test-project");
        config.setSourceLevel(SourceLevel.JAVA_8);
        config.setEncoding("UTF-8");
        return config;
    }
}
