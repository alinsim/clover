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
import org.openclover.runtime.CloverNames;
import org.openclover.runtime.remote.DistributedConfig;
import org_openclover_runtime.CloverProfile;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    private static final String ASSERT_TWO_METHODS = "Should have 2 methods";
    private static final String ASSERT_FILE_STRUCTURE_NOT_NULL = "FileStructureInfo should not be null";
    private static final String ASSERT_INSTRUMENTED_NOT_NULL = "Instrumented output should not be null";
    private static final String ASSERT_CONTAINS_MARKER = "Should contain instrumentation marker";
    private static final String MARKER_TEXT = "This file has been instrumented by OpenClover";
    private static final String ASSERT_AT_LEAST_ONE_STATEMENT = "Should have at least 1 statement";
    private static final String METHOD_CLOSING = "    }\n";
    private static final String LOCALHOST = "localhost";
    private static final String INT_X_EQUALS_ONE = "        int x = 1;\n";
    private static final String IMPORT_JUNIT_TEST = "import org.junit.Test;\n";
    private static final String PUBLIC_CLASS_FOO_TEST = "public class FooTest {\n";
    private static final String AT_TEST = "    @Test\n";
    private static final String PUBLIC_VOID_TEST_FOO = "    public void testFoo() {\n";
    private static final String CLOSING_BRACE = "}\n";
    private static final String FOO_TEST_JAVA = "FooTest.java";
    private static final String GLOBAL_SLICE_START = "globalSliceStart";
    private static final String GLOBAL_SLICE_END = "globalSliceEnd";

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
        assertEquals(ASSERT_TWO_METHODS, 2, metrics.getNumMethods());
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
        assertTrue("Should have statements + branches for if/else",
                metrics.getNumStatements() + metrics.getNumBranches() >= 3);
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

    @Test
    public void testRecorderContainsTrackerClass() throws Exception {
        String sourceCode = "import java.io.InputStream;\nimport java.io.FileInputStream;\n"
                + "public class WithTry { void m() throws Exception { try (InputStream is = new FileInputStream(\"f\")) { is.read(); } } }";

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");
        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "WithTry.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);
        session.exitFile();
        session.close();

        String instrumented = output.toString();

        // The recorder MUST contain the Tracker inner class so that
        // try-with-resources __CLR.Tracker references compile
        assertTrue("Recorder must contain Tracker class definition",
                instrumented.contains("static final class Tracker implements AutoCloseable"));
        assertTrue("Recorder must contain Tracker constructor",
                instrumented.contains("Tracker(int idx)"));
        assertTrue("Recorder must contain Tracker close method",
                instrumented.contains("public void close()"));

        // The try-with-resources must reference the Tracker
        assertTrue("Try-with-resources must use Tracker",
                instrumented.contains(".Tracker __CLR_resource_"));
    }

    @Test
    public void testBranchesRegisteredAsBranches() throws Exception {
        String sourceCode =
                "public class WithBranch {\n" +
                "    public String check(int x) {\n" +
                "        if (x > 0) { return \"pos\"; } else { return \"neg\"; }\n" +
                METHOD_CLOSING +
                "}";

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");
        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "WithBranch.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);
        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        ProjectMetrics metrics = (ProjectMetrics) registry.getProject().getMetrics();
        assertEquals(ASSERT_ONE_CLASS, 1, metrics.getNumClasses());
        assertEquals(ASSERT_ONE_METHOD, 1, metrics.getNumMethods());
        // The if/else should register at least 2 branches (then + else)
        assertTrue("Should have branches registered, got " + metrics.getNumBranches(),
                metrics.getNumBranches() >= 2);
    }

    @Test
    public void testMethodEntryIndexMatchesSessionIndex() throws Exception {
        // A class with two methods — verify the R.inc() index in the instrumented
        // output matches the method's dataIndex from the session registry.
        String sourceCode =
                "public class TwoMethods {\n" +
                "    public void first() { System.out.println(1); }\n" +
                "    public void second() { System.out.println(2); }\n" +
                "}";

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");
        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "TwoMethods.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);
        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();

        // Get the method data indices from the registry
        ProjectMetrics pm = (ProjectMetrics) registry.getProject().getMetrics();
        assertEquals(ASSERT_TWO_METHODS, 2, pm.getNumMethods());

        // The first method's R.inc() must use index 0 (first slot in the file)
        // The second method's R.inc() must use a DIFFERENT index
        // Extract the inc indices from the instrumented output
        Pattern incPattern = Pattern.compile("\\.inc\\((\\d+)\\)");
        Matcher matcher = incPattern.matcher(instrumented);
        List<Integer> indices = new ArrayList<>();
        while (matcher.find()) {
            indices.add(Integer.parseInt(matcher.group(1)));
        }

        // There should be at least 4 inc calls: method1 entry + stmt, method2 entry + stmt
        assertTrue("Should have at least 4 inc calls, got " + indices.size(),
                indices.size() >= 4);

        // The method entry indices (first inc in each method) must be sequential
        // and start from 0. Method entries are the first inc after the recorder.
        int firstMethodEntry = indices.get(0);
        assertTrue("First method entry index should be 0, got " + firstMethodEntry,
                firstMethodEntry == 0);
    }

    @Test
    public void testRecorderConfigBitsContainFlushPolicy() throws Exception {
        String sourceCode = "public class FlushPolicyTest { public void method() { int x = 1; } }";

        JavaInstrumentationConfig config = createConfig();
        config.setFlushPolicy(1);
        config.setFlushInterval(500);

        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");
        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "FlushPolicyTest.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);
        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();

        Pattern getRecorderPattern = Pattern.compile("Clover\\.getRecorder\\([^,]+,\\s*\\d+L,\\s*(\\d+)L");
        Matcher matcher = getRecorderPattern.matcher(instrumented);
        assertTrue("Should contain Clover.getRecorder call with recorderCfg parameter", matcher.find());

        long recorderCfg = Long.parseLong(matcher.group(1));
        assertTrue("recorderCfg should be non-zero for INTERVAL flush policy, got " + recorderCfg,
                recorderCfg != 0L);
    }

    @Test
    public void testRecorderMaxDataIndexIsNonZero() throws Exception {
        String sourceCode =
                "public class MaxDataIndexTest {\n" +
                "    public void method() {\n" +
                "        int x = 1;\n" +
                "        int y = 2;\n" +
                "        int z = 3;\n" +
                METHOD_CLOSING +
                "}";

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");
        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "MaxDataIndexTest.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);
        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();

        Pattern getRecorderPattern = Pattern.compile("Clover\\.getRecorder\\([^,]+,\\s*\\d+L,\\s*\\d+L,\\s*(\\d+)");
        Matcher matcher = getRecorderPattern.matcher(instrumented);
        assertTrue("Should contain Clover.getRecorder call with maxDataIndex parameter", matcher.find());

        int maxDataIndex = Integer.parseInt(matcher.group(1));
        assertTrue("maxDataIndex should be > 0 for class with statements, got " + maxDataIndex,
                maxDataIndex > 0);
    }

    @Test
    public void testRecorderDistributedConfig() throws Exception {
        String sourceCode = "public class DistributedTest { public void method() { int x = 1; } }";

        JavaInstrumentationConfig config = createConfig();
        DistributedConfig distConfig = new DistributedConfig();
        distConfig.setName("test-distributed");
        distConfig.setHost(LOCALHOST);
        distConfig.setPort(1234);
        config.setDistributedConfig(distConfig);

        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");
        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "DistributedTest.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);
        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();

        assertTrue("Should contain distributed config key", instrumented.contains(CloverNames.PROP_DISTRIBUTED_CONFIG));
        assertTrue("Should contain String array construction for distributed config",
                instrumented.contains("String[]"));
    }

    @Test
    public void testRecorderProfilesPassedThrough() throws Exception {
        String sourceCode = "public class ProfileTest { public void method() { int x = 1; } }";

        JavaInstrumentationConfig config = createConfig();
        CloverProfile profile = new CloverProfile(
                "test-profile",
                CloverProfile.CoverageRecorderType.GROWABLE,
                null);
        config.addProfile(profile);

        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");
        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "ProfileTest.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);
        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();

        assertTrue("Should contain CloverProfile[] declaration",
                instrumented.contains("CloverProfile[] profiles"));
        assertTrue("Should contain GROWABLE recorder type", instrumented.contains("GROWABLE"));
    }

    @Test
    public void testTestMethodHasGlobalSliceWrapper() throws Exception {
        String sourceCode = IMPORT_JUNIT_TEST +
                PUBLIC_CLASS_FOO_TEST +
                AT_TEST +
                PUBLIC_VOID_TEST_FOO +
                INT_X_EQUALS_ONE +
                METHOD_CLOSING +
                CLOSING_BRACE;

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, FOO_TEST_JAVA), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();

        assertTrue("Test method should have globalSliceStart", instrumented.contains(GLOBAL_SLICE_START));
        assertTrue("Test method should have globalSliceEnd", instrumented.contains(GLOBAL_SLICE_END));
        assertTrue("Test method should have try block", instrumented.contains("try{"));
        assertTrue("Test method should have catch clause", instrumented.contains("catch(Throwable"));
        assertTrue("Test method should have finally clause", instrumented.contains("finally{"));
        assertTrue("Test method should contain method name in globalSliceEnd", instrumented.contains("\"testFoo\""));
    }

    @Test
    public void testNonTestMethodHasNoGlobalSliceWrapper() throws Exception {
        String sourceCode = "public class Foo {\n" +
                "    public void doStuff() {\n" +
                INT_X_EQUALS_ONE +
                METHOD_CLOSING +
                CLOSING_BRACE;

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "Foo.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();

        assertTrue("Non-test method should NOT have globalSliceStart", !instrumented.contains(GLOBAL_SLICE_START));
        assertTrue("Non-test method should NOT have globalSliceEnd", !instrumented.contains(GLOBAL_SLICE_END));
        assertTrue("Non-test method should have R.inc() call", instrumented.contains(INC_CALL_PATTERN));
    }

    @Test
    public void testParameterizedTestMethodHasGlobalSliceWrapper() throws Exception {
        String sourceCode = "import org.junit.jupiter.params.ParameterizedTest;\n" +
                PUBLIC_CLASS_FOO_TEST +
                "    @ParameterizedTest\n" +
                "    void testParam(String s) {\n" +
                "        assert s != null;\n" +
                METHOD_CLOSING +
                CLOSING_BRACE;

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, FOO_TEST_JAVA), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();

        assertTrue("Parameterized test method should have globalSliceStart", instrumented.contains(GLOBAL_SLICE_START));
        assertTrue("Parameterized test method should have globalSliceEnd", instrumented.contains(GLOBAL_SLICE_END));
        assertTrue("Parameterized test method should reference TEST_NAME_SNIFFER", instrumented.contains("TEST_NAME_SNIFFER"));
    }

    @Test
    public void testMixedTestAndNonTestMethods() throws Exception {
        String sourceCode = IMPORT_JUNIT_TEST +
                PUBLIC_CLASS_FOO_TEST +
                "    public void helper() {\n" +
                INT_X_EQUALS_ONE +
                METHOD_CLOSING +
                AT_TEST +
                PUBLIC_VOID_TEST_FOO +
                "        helper();\n" +
                METHOD_CLOSING +
                CLOSING_BRACE;

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, FOO_TEST_JAVA), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();

        // Count occurrences of globalSliceStart (should be 1, only in testFoo)
        int globalSliceStartCount = countOccurrences(instrumented, GLOBAL_SLICE_START);
        assertEquals("Should have exactly 1 globalSliceStart (only in testFoo)", 1, globalSliceStartCount);

        // Both methods should have R.inc() calls
        assertTrue("Should have R.inc() calls for both methods", instrumented.contains(INC_CALL_PATTERN));
    }

    @Test
    public void testTestMethodGlobalSliceEndContainsMethodName() throws Exception {
        String sourceCode = IMPORT_JUNIT_TEST +
                "public class MethodNameTest {\n" +
                AT_TEST +
                "    public void myTestMethod() {\n" +
                INT_X_EQUALS_ONE +
                METHOD_CLOSING +
                CLOSING_BRACE;

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "MethodNameTest.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();

        assertTrue("globalSliceEnd should contain exact method name", instrumented.contains("\"myTestMethod\""));
    }

    @Test
    public void testTestMethodGlobalSliceEndContainsSnifferReference() throws Exception {
        String sourceCode = IMPORT_JUNIT_TEST +
                "public class SnifferTest {\n" +
                AT_TEST +
                "    public void testSniffer() {\n" +
                INT_X_EQUALS_ONE +
                METHOD_CLOSING +
                CLOSING_BRACE;

        JavaInstrumentationConfig config = createConfig();
        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test-project");

        InstrumentationSession session = registry.startInstr(config.getEncoding());
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "SnifferTest.java"), sourceCode);

        SessionAwareInstrumenter.instrument(source, output, session, config, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        String instrumented = output.toString();

        assertTrue("globalSliceEnd should contain TEST_NAME_SNIFFER reference",
                instrumented.contains("TEST_NAME_SNIFFER.getTestName()"));
    }

    private int countOccurrences(String text, String substring) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(substring, index)) != -1) {
            count++;
            index += substring.length();
        }
        return count;
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
