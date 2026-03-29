package org.openclover.core.reporters.agent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openclover.core.CloverDatabase;
import org.openclover.core.CoverageData;
import org.openclover.core.api.instrumentation.InstrumentationSession;
import org.openclover.core.api.registry.BranchInfo;
import org.openclover.core.api.registry.ClassInfo;
import org.openclover.core.api.registry.HasMetricsFilter;
import org.openclover.core.api.registry.MethodInfo;
import org.openclover.core.api.registry.StatementInfo;
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig;
import org.openclover.core.cfg.instr.java.SourceLevel;
import org.openclover.core.instr.java.StringInstrumentationSource;
import org.openclover.core.instr.java.javaparser.SessionAwareInstrumenter;
import org.openclover.core.recorder.InMemPerTestCoverage;
import org.openclover.core.registry.Clover2Registry;
import org.openclover.core.registry.entities.FullFileInfo;
import org.openclover.core.registry.entities.FullTestCaseInfo;
import org.openclover.core.util.FileUtils;
import org.openclover.runtime.registry.format.RegAccessMode;

import java.io.File;
import java.io.StringWriter;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Integration tests for AgentCoverageQuery using real instrumentation and coverage data.
 */
public class AgentCoverageQueryTest {

    private static final String TEST_PROJECT = "test-project";
    private static final String NON_EXISTENT_PKG = "com.example";
    private static final String NON_EXISTENT_TEST = NON_EXISTENT_PKG + ".NonExistent.testBar";
    private static final String UTF_8 = "UTF-8";
    private static final String SUMMARY_NOT_NULL = "Summary should not be null";
    private static final String METRICS_NOT_NULL = "Metrics should not be null";
    private static final String RESULT_NOT_NULL = "Result should not be null";
    private static final String PUBLIC_CLASS_CALCULATOR = "public class Calculator {\n";
    private static final String METHOD_CLOSING = "    }\n";
    private static final String CALCULATOR_JAVA = "Calculator.java";
    private static final String PACKAGE_COM_EXAMPLE = "package com.example;\n";
    private static final String PUBLIC_CLASS_EXAMPLE = "public class Example {\n";
    private static final String PUBLIC_VOID_DO_SOMETHING = "    public void doSomething() {\n";
    private static final String SYSOUT_TEST = "        System.out.println(\"test\");\n";
    private static final String EXAMPLE_JAVA = "Example.java";
    private static final String COM_EXAMPLE_EXAMPLE_JAVA = "com/example/Example.java";
    private static final String SHOULD_RETURN_CORRECT_PACKAGE_PATH = "SHOULD_RETURN_CORRECT_PACKAGE_PATH";

    private File workingDir;
    private File registryFile;
    private CloverDatabase database;

    @Before
    public void setUp() throws Exception {
        workingDir = File.createTempFile(getClass().getName(), ".tmp");
        workingDir.delete();
        workingDir.mkdir();

        registryFile = new File(workingDir, "clover.db");
    }

    @After
    public void tearDown() throws Exception {
        if (workingDir != null && workingDir.exists()) {
            FileUtils.deltree(workingDir);
        }
        FullTestCaseInfo.Factory.reset();
    }

    @Test
    public void testGetTestCoverageNotFoundReturnsNull() throws Exception {
        Clover2Registry registry = new Clover2Registry(registryFile, RegAccessMode.READWRITE, TEST_PROJECT);
        InMemPerTestCoverage perTestCoverage = new InMemPerTestCoverage(10);
        CoverageData coverageData = new CoverageData(0, new int[10], perTestCoverage);
        database = new CloverDatabase(registry);
        registry.setCoverageData(coverageData);
        registry.getProject().setDataProvider(coverageData);

        AgentCoverageQuery query = new AgentCoverageQuery(database);

        TestCoverageResult result = query.getTestCoverage(NON_EXISTENT_TEST);

        assertNull("Result should be null for non-existent test", result);
    }

    @Test
    public void testGetFileUncoveredNotFoundReturnsNull() throws Exception {
        Clover2Registry registry = new Clover2Registry(registryFile, RegAccessMode.READWRITE, TEST_PROJECT);
        InMemPerTestCoverage perTestCoverage = new InMemPerTestCoverage(10);
        CoverageData coverageData = new CoverageData(0, new int[10], perTestCoverage);
        database = new CloverDatabase(registry);
        registry.setCoverageData(coverageData);
        registry.getProject().setDataProvider(coverageData);

        AgentCoverageQuery query = new AgentCoverageQuery(database);

        FileUncoveredResult result = query.getFileUncovered("NonExistentClass");

        assertNull("Result should be null for non-existent class", result);
    }

    @Test
    public void testGetProjectSummary() throws Exception {
        Clover2Registry registry = new Clover2Registry(registryFile, RegAccessMode.READWRITE, TEST_PROJECT);
        InMemPerTestCoverage perTestCoverage = new InMemPerTestCoverage(10);
        CoverageData coverageData = new CoverageData(0, new int[10], perTestCoverage);
        database = new CloverDatabase(registry);
        registry.setCoverageData(coverageData);
        registry.getProject().setDataProvider(coverageData);

        AgentCoverageQuery query = new AgentCoverageQuery(database);

        CoverageSummary summary = query.getProjectSummary();

        assertNotNull(SUMMARY_NOT_NULL, summary);
        assertNotNull(METRICS_NOT_NULL, summary.metrics);
    }

    @Test
    public void testGetAllTestsSummary() throws Exception {
        Clover2Registry registry = new Clover2Registry(registryFile, RegAccessMode.READWRITE, TEST_PROJECT);
        InMemPerTestCoverage perTestCoverage = new InMemPerTestCoverage(10);
        CoverageData coverageData = new CoverageData(0, new int[10], perTestCoverage);
        database = new CloverDatabase(registry);
        registry.setCoverageData(coverageData);
        registry.getProject().setDataProvider(coverageData);

        AgentCoverageQuery query = new AgentCoverageQuery(database);

        List<TestSummary> tests = query.getAllTests();

        assertNotNull("Tests list should not be null", tests);
    }

    @Test
    public void testGetFileUncoveredReturnsUncoveredLines() throws Exception {
        // Instrument a class with 3 statements
        String sourceCode =
                PUBLIC_CLASS_CALCULATOR +
                "    public int calculate(int x) {\n" +
                "        int result = x * 2;\n" +
                "        result = result + 1;\n" +
                "        return result;\n" +
                METHOD_CLOSING +
                "}";

        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, TEST_PROJECT);
        InstrumentationSession session = registry.startInstr(UTF_8);
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, CALCULATOR_JAVA), sourceCode);
        SessionAwareInstrumenter.instrument(source, output, session, createConfig(), null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        // Get the instrumented file info
        FullFileInfo fileInfo = (FullFileInfo) registry.getProject().getFiles((HasMetricsFilter) null).get(0);
        List<StatementInfo> statements = fileInfo.getStatements();

        // Simulate coverage: first 2 statements covered, third uncovered
        int maxIndex = session.getCurrentFileMaxIndex();
        int[] hitCounts = new int[maxIndex];
        hitCounts[statements.get(0).getDataIndex()] = 1;
        hitCounts[statements.get(1).getDataIndex()] = 1;
        // statements.get(2) left at 0 = uncovered

        InMemPerTestCoverage perTestCoverage = new InMemPerTestCoverage(maxIndex);
        CoverageData coverageData = new CoverageData(0, hitCounts, perTestCoverage);

        registry.setCoverageData(coverageData);
        fileInfo.setDataProvider(coverageData);
        registry.getProject().setDataProvider(coverageData);

        database = new CloverDatabase(registry);
        AgentCoverageQuery query = new AgentCoverageQuery(database);

        // Query for uncovered elements
        FileUncoveredResult result = query.getFileUncovered("Calculator");

        assertNotNull(RESULT_NOT_NULL, result);
        assertFalse("Should have uncovered lines", result.uncoveredLines.isEmpty());
        assertTrue("Should contain line 5 (return statement)",
                result.uncoveredLines.contains(statements.get(2).getStartLine()));
    }

    @Test
    public void testGetFileUncoveredReturnsBranchDetail() throws Exception {
        // Instrument a class with an if-else branch
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

        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, TEST_PROJECT);
        InstrumentationSession session = registry.startInstr(UTF_8);
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "Conditional.java"), sourceCode);
        SessionAwareInstrumenter.instrument(source, output, session, createConfig(), null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        // Get the instrumented file info
        FullFileInfo fileInfo = (FullFileInfo) registry.getProject().getFiles((HasMetricsFilter) null).get(0);

        // Get branches from the method
        BranchInfo branch = null;
        outerLoop:
        for (ClassInfo classInfo : fileInfo.getClasses()) {
            for (MethodInfo methodInfo : classInfo.getMethods()) {
                if (!methodInfo.getBranches().isEmpty()) {
                    branch = methodInfo.getBranches().get(0);
                    break outerLoop;
                }
            }
        }

        // Simulate coverage: true branch covered, false branch uncovered
        int maxIndex = session.getCurrentFileMaxIndex();
        int[] hitCounts = new int[maxIndex];

        // Cover the true branch only (dataIndex for true, dataIndex+1 for false)
        if (branch != null) {
            hitCounts[branch.getDataIndex()] = 1;      // true branch covered
            hitCounts[branch.getDataIndex() + 1] = 0;  // false branch uncovered
        }

        InMemPerTestCoverage perTestCoverage = new InMemPerTestCoverage(maxIndex);
        CoverageData coverageData = new CoverageData(0, hitCounts, perTestCoverage);

        registry.setCoverageData(coverageData);
        fileInfo.setDataProvider(coverageData);
        registry.getProject().setDataProvider(coverageData);

        database = new CloverDatabase(registry);
        AgentCoverageQuery query = new AgentCoverageQuery(database);

        // Query for uncovered elements
        FileUncoveredResult result = query.getFileUncovered("Conditional");

        assertNotNull(RESULT_NOT_NULL, result);
        assertNotNull("Branch should be found", branch);
        assertFalse("Should have uncovered branches", result.uncoveredBranches.isEmpty());

        // Find the uncovered false branch
        final BranchInfo finalBranch = branch;
        UncoveredBranch uncoveredBranch = result.uncoveredBranches.stream()
                .filter(ub -> "false".equals(ub.type))
                .findFirst()
                .orElse(null);

        assertNotNull("Should have uncovered false branch", uncoveredBranch);
        assertEquals("Branch should be at line 3", finalBranch.getStartLine(), uncoveredBranch.line);
        assertEquals("Should have method name", "classify", uncoveredBranch.method);
    }

    @Test
    public void testGetProjectSummaryMethodMetricsNonZero() throws Exception {
        // Instrument a class with methods
        String sourceCode =
                PUBLIC_CLASS_CALCULATOR +
                "    public int add(int a, int b) {\n" +
                "        return a + b;\n" +
                METHOD_CLOSING +
                "    public int subtract(int a, int b) {\n" +
                "        return a - b;\n" +
                METHOD_CLOSING +
                "}";

        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, TEST_PROJECT);
        InstrumentationSession session = registry.startInstr(UTF_8);
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, CALCULATOR_JAVA), sourceCode);
        SessionAwareInstrumenter.instrument(source, output, session, createConfig(), null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        // Get the instrumented file info
        FullFileInfo fileInfo = (FullFileInfo) registry.getProject().getFiles((HasMetricsFilter) null).get(0);
        List<StatementInfo> statements = fileInfo.getStatements();

        // Simulate coverage for first method only
        int maxIndex = session.getCurrentFileMaxIndex();
        int[] hitCounts = new int[maxIndex];
        if (!statements.isEmpty()) {
            hitCounts[statements.get(0).getDataIndex()] = 1;
        }

        InMemPerTestCoverage perTestCoverage = new InMemPerTestCoverage(maxIndex);
        CoverageData coverageData = new CoverageData(0, hitCounts, perTestCoverage);

        registry.setCoverageData(coverageData);
        fileInfo.setDataProvider(coverageData);
        registry.getProject().setDataProvider(coverageData);

        database = new CloverDatabase(registry);
        AgentCoverageQuery query = new AgentCoverageQuery(database);

        // Query for project summary
        CoverageSummary summary = query.getProjectSummary();

        assertNotNull(SUMMARY_NOT_NULL, summary);
        assertNotNull(METRICS_NOT_NULL, summary.metrics);
        assertNotNull("Method metrics should not be null", summary.metrics.methods);
        assertTrue("Should have methods", summary.metrics.methods.total > 0);
        assertEquals("Should have 2 methods", 2, summary.metrics.methods.total);
    }

    @Test
    public void testGetFileUncoveredBySimpleName() throws Exception {
        // Instrument a class
        String sourceCode =
                PACKAGE_COM_EXAMPLE +
                PUBLIC_CLASS_EXAMPLE +
                PUBLIC_VOID_DO_SOMETHING +
                SYSOUT_TEST +
                METHOD_CLOSING +
                "}";

        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, TEST_PROJECT);
        InstrumentationSession session = registry.startInstr(UTF_8);
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, EXAMPLE_JAVA), sourceCode);
        SessionAwareInstrumenter.instrument(source, output, session, createConfig(), null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        int maxIndex = session.getCurrentFileMaxIndex();
        int[] hitCounts = new int[maxIndex];
        InMemPerTestCoverage perTestCoverage = new InMemPerTestCoverage(maxIndex);
        CoverageData coverageData = new CoverageData(0, hitCounts, perTestCoverage);

        FullFileInfo fileInfo = (FullFileInfo) registry.getProject().getFiles((HasMetricsFilter) null).get(0);
        registry.setCoverageData(coverageData);
        fileInfo.setDataProvider(coverageData);
        registry.getProject().setDataProvider(coverageData);

        database = new CloverDatabase(registry);
        AgentCoverageQuery query = new AgentCoverageQuery(database);

        // Query using simple class name (no .java suffix, no package)
        FileUncoveredResult result = query.getFileUncovered("Example");

        assertNotNull("Should find file by simple name", result);
        assertEquals(SHOULD_RETURN_CORRECT_PACKAGE_PATH, COM_EXAMPLE_EXAMPLE_JAVA, result.file);
    }

    @Test
    public void testGetFileUncoveredByPackagePath() throws Exception {
        // Instrument a class
        String sourceCode =
                PACKAGE_COM_EXAMPLE +
                PUBLIC_CLASS_EXAMPLE +
                PUBLIC_VOID_DO_SOMETHING +
                SYSOUT_TEST +
                METHOD_CLOSING +
                "}";

        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, TEST_PROJECT);
        InstrumentationSession session = registry.startInstr(UTF_8);
        StringWriter output = new StringWriter();

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, EXAMPLE_JAVA), sourceCode);
        SessionAwareInstrumenter.instrument(source, output, session, createConfig(), null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        int maxIndex = session.getCurrentFileMaxIndex();
        int[] hitCounts = new int[maxIndex];
        InMemPerTestCoverage perTestCoverage = new InMemPerTestCoverage(maxIndex);
        CoverageData coverageData = new CoverageData(0, hitCounts, perTestCoverage);

        FullFileInfo fileInfo = (FullFileInfo) registry.getProject().getFiles((HasMetricsFilter) null).get(0);
        registry.setCoverageData(coverageData);
        fileInfo.setDataProvider(coverageData);
        registry.getProject().setDataProvider(coverageData);

        database = new CloverDatabase(registry);
        AgentCoverageQuery query = new AgentCoverageQuery(database);

        // Query using package path
        FileUncoveredResult result = query.getFileUncovered(COM_EXAMPLE_EXAMPLE_JAVA);

        assertNotNull("Should find file by package path", result);
        assertEquals(SHOULD_RETURN_CORRECT_PACKAGE_PATH, COM_EXAMPLE_EXAMPLE_JAVA, result.file);
    }

    private JavaInstrumentationConfig createConfig() {
        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setSourceLevel(SourceLevel.JAVA_17);
        config.setEncoding(UTF_8);
        return config;
    }
}
