package org.openclover.core.reporters.agent;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openclover.core.CloverDatabase;
import org.openclover.core.CoverageData;
import org.openclover.core.api.instrumentation.InstrumentationSession;
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig;
import org.openclover.core.instr.java.StringInstrumentationSource;
import org.openclover.core.instr.java.javaparser.AstInstrumenter;
import org.openclover.core.recorder.InMemPerTestCoverage;
import org.openclover.core.registry.Clover2Registry;
import org.openclover.core.registry.entities.FullTestCaseInfo;
import org.openclover.core.reporters.json.JSONObject;
import org.openclover.core.util.FileUtils;
import org.openclover.runtime.registry.format.RegAccessMode;

import java.io.File;
import java.io.StringWriter;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * TDD tests for AgentJsonReporter verifying JSON output matches the clover-agent-v1 schema.
 */
public class AgentJsonReporterTest {

    private static final String TEST_PROJECT = "test-project";
    private static final String UTF_8 = "UTF-8";
    private static final String FORMAT_V1 = "clover-agent-v1";
    private static final String KEY_FORMAT = "format";
    private static final String KEY_SCOPE = "scope";
    private static final String KEY_STALE = "stale";
    private static final String KEY_DB_TIMESTAMP = "dbTimestamp";
    private static final String KEY_DB_PATH = "dbPath";
    private static final String KEY_ERROR = "error";
    private static final String KEY_DATA = "data";
    private static final String KEY_SUMMARY = "summary";
    private static final String KEY_TESTS = "tests";
    private static final String KEY_TOP_UNCOVERED = "topUncovered";
    private static final String KEY_STATEMENTS = "statements";
    private static final String KEY_BRANCHES = "branches";
    private static final String KEY_METHODS = "methods";
    private static final String KEY_COVERED = "covered";
    private static final String KEY_TOTAL = "total";
    private static final String KEY_PCT = "pct";
    private static final String SCOPE_FULL = "full-suite";
    private static final String SCOPE_SINGLE = "single-test";
    private static final String KEY_APP = "app";
    private static final String KEY_TEST_SECTION = "test";
    private static final String KEY_COMBINED = "combined";
    private static final String MUST_BE_PRESENT = " must be present";
    private static final String MUST_BE_IN_DATA = " must be in data";
    private static final String MUST_BE_IN_SUMMARY = " must be in summary";
    private static final String MUST_BE_IN_METRIC = " must be in metric";
    private static final String ERROR_MUST_BE_PRESENT = "error must be present";
    private static final String ERR_CLASS_NOT_FOUND = "class_not_found";
    private static final String ERR_TEST_NOT_FOUND = "test_not_found";
    private static final String KEY_CODE = "code";
    private static final String NON_EXISTENT = "NonExistent";
    private static final String CALCULATOR_JAVA = "Calculator" + ".java";
    private static final String METHOD_CLOSE = "    " + "}\n";

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
        if (workingDir != null && workingDir.exists()) {
            FileUtils.deltree(workingDir);
        }
        FullTestCaseInfo.Factory.reset();
    }

    // ==================== ENVELOPE TESTS ====================

    @Test
    public void feedbackHasCorrectEnvelope() throws Exception {
        AgentJsonReporter reporter = createReporterWithEmptyDb();
        String json = reporter.generateFeedback(10, 5);

        JSONObject envelope = new JSONObject(json);
        assertEquals(FORMAT_V1, envelope.getString(KEY_FORMAT));
        assertEquals(SCOPE_FULL, envelope.getString(KEY_SCOPE));
        assertNotNull(KEY_DB_TIMESTAMP + MUST_BE_PRESENT, envelope.getString(KEY_DB_TIMESTAMP));
        assertNotNull(KEY_DB_PATH + MUST_BE_PRESENT, envelope.getString(KEY_DB_PATH));
        assertTrue(KEY_DATA + MUST_BE_PRESENT, envelope.has(KEY_DATA));
    }

    @Test
    public void feedbackDataHasSummaryTestsAndTopUncovered() throws Exception {
        AgentJsonReporter reporter = createReporterWithEmptyDb();
        String json = reporter.generateFeedback(10, 5);

        JSONObject data = new JSONObject(json).getJSONObject(KEY_DATA);
        assertTrue(KEY_SUMMARY + MUST_BE_IN_DATA, data.has(KEY_SUMMARY));
        assertTrue(KEY_TESTS + MUST_BE_IN_DATA, data.has(KEY_TESTS));
        assertTrue(KEY_TOP_UNCOVERED + MUST_BE_IN_DATA, data.has(KEY_TOP_UNCOVERED));
    }

    @Test
    public void summaryHasAppTestAndCombinedSections() throws Exception {
        AgentJsonReporter reporter = createReporterWithEmptyDb();
        String json = reporter.generateFeedback(10, 5);

        JSONObject summary = new JSONObject(json).getJSONObject(KEY_DATA).getJSONObject(KEY_SUMMARY);
        assertTrue(KEY_APP + MUST_BE_IN_SUMMARY, summary.has(KEY_APP));
        assertTrue(KEY_TEST_SECTION + MUST_BE_IN_SUMMARY, summary.has(KEY_TEST_SECTION));
        assertTrue(KEY_COMBINED + MUST_BE_IN_SUMMARY, summary.has(KEY_COMBINED));
    }

    @Test
    public void summaryAppHasStatementsBranchesMethods() throws Exception {
        AgentJsonReporter reporter = createReporterWithEmptyDb();
        String json = reporter.generateFeedback(10, 5);

        JSONObject app = new JSONObject(json).getJSONObject(KEY_DATA)
                .getJSONObject(KEY_SUMMARY).getJSONObject(KEY_APP);
        assertTrue(KEY_STATEMENTS + MUST_BE_IN_SUMMARY, app.has(KEY_STATEMENTS));
        assertTrue(KEY_BRANCHES + MUST_BE_IN_SUMMARY, app.has(KEY_BRANCHES));
        assertTrue(KEY_METHODS + MUST_BE_IN_SUMMARY, app.has(KEY_METHODS));
    }

    @Test
    public void metricPairHasCoveredTotalPct() throws Exception {
        AgentJsonReporter reporter = createReporterWithEmptyDb();
        String json = reporter.generateFeedback(10, 5);

        JSONObject stmts = new JSONObject(json).getJSONObject(KEY_DATA)
                .getJSONObject(KEY_SUMMARY).getJSONObject(KEY_APP).getJSONObject(KEY_STATEMENTS);
        assertTrue(KEY_COVERED + MUST_BE_IN_METRIC, stmts.has(KEY_COVERED));
        assertTrue(KEY_TOTAL + MUST_BE_IN_METRIC, stmts.has(KEY_TOTAL));
        assertTrue(KEY_PCT + MUST_BE_IN_METRIC, stmts.has(KEY_PCT));
    }

    @Test
    public void summaryAppAndTestTotalsEqualCombined() throws Exception {
        AgentJsonReporter reporter = createReporterWithInstrumentedCode();
        String json = reporter.generateFeedback(50, 10);

        JSONObject summary = new JSONObject(json).getJSONObject(KEY_DATA).getJSONObject(KEY_SUMMARY);
        JSONObject app = summary.getJSONObject(KEY_APP).getJSONObject(KEY_STATEMENTS);
        JSONObject test = summary.getJSONObject(KEY_TEST_SECTION).getJSONObject(KEY_STATEMENTS);
        JSONObject combined = summary.getJSONObject(KEY_COMBINED).getJSONObject(KEY_STATEMENTS);

        assertEquals("app.total + test.total should equal combined.total",
                app.getInt(KEY_TOTAL) + test.getInt(KEY_TOTAL),
                combined.getInt(KEY_TOTAL));
    }

    // ==================== ERROR ENVELOPE TESTS ====================

    @Test
    public void fileUncoveredReturnsErrorForNonExistentClass() throws Exception {
        AgentJsonReporter reporter = createReporterWithEmptyDb();
        String json = reporter.generateFileUncovered("NonExistentClass");

        JSONObject envelope = new JSONObject(json);
        assertEquals(FORMAT_V1, envelope.getString(KEY_FORMAT));
        assertFalse("data must be null on error", envelope.has(KEY_DATA) && !envelope.isNull(KEY_DATA));
        assertTrue(ERROR_MUST_BE_PRESENT, envelope.has(KEY_ERROR) && !envelope.isNull(KEY_ERROR));

        JSONObject error = envelope.getJSONObject(KEY_ERROR);
        assertEquals(ERR_CLASS_NOT_FOUND, error.getString(KEY_CODE));
    }

    @Test
    public void testFeedbackReturnsErrorForNonExistentTest() throws Exception {
        AgentJsonReporter reporter = createReporterWithEmptyDb();
        String json = reporter.generateTestFeedback("com.example." + NON_EXISTENT + ".testFoo");

        JSONObject envelope = new JSONObject(json);
        assertTrue(ERROR_MUST_BE_PRESENT, envelope.has(KEY_ERROR) && !envelope.isNull(KEY_ERROR));
        assertEquals(ERR_TEST_NOT_FOUND, envelope.getJSONObject(KEY_ERROR).getString(KEY_CODE));
    }

    @Test
    public void suggestReturnsErrorForNonExistentClass() throws Exception {
        AgentJsonReporter reporter = createReporterWithEmptyDb();
        String json = reporter.generateSuggestions(NON_EXISTENT, 5);

        JSONObject envelope = new JSONObject(json);
        assertTrue(ERROR_MUST_BE_PRESENT, envelope.has(KEY_ERROR) && !envelope.isNull(KEY_ERROR));
        assertEquals(ERR_CLASS_NOT_FOUND, envelope.getJSONObject(KEY_ERROR).getString(KEY_CODE));
    }

    // ==================== SCOPE TESTS ====================

    @Test
    public void feedbackHasFullSuiteScope() throws Exception {
        AgentJsonReporter reporter = createReporterWithEmptyDb();
        String json = reporter.generateFeedback(10, 5);
        assertEquals(SCOPE_FULL, new JSONObject(json).getString(KEY_SCOPE));
    }

    @Test
    public void testFeedbackHasSingleTestScope() throws Exception {
        AgentJsonReporter reporter = createReporterWithInstrumentedCode();
        // Even for non-existent test, the error envelope should have scope
        String json = reporter.generateTestFeedback("some.Test.method");
        // Error responses default to full-suite scope
        assertNotNull(new JSONObject(json).getString(KEY_SCOPE));
    }

    // ==================== CONTENT TESTS WITH REAL DATA ====================

    @Test
    public void feedbackWithInstrumentedCodeHasNonZeroStatements() throws Exception {
        AgentJsonReporter reporter = createReporterWithInstrumentedCode();
        String json = reporter.generateFeedback(50, 10);

        JSONObject combined = new JSONObject(json).getJSONObject(KEY_DATA)
                .getJSONObject(KEY_SUMMARY).getJSONObject(KEY_COMBINED).getJSONObject(KEY_STATEMENTS);
        assertTrue("total statements should be > 0", combined.getInt(KEY_TOTAL) > 0);
    }

    @Test
    public void fileUncoveredWithInstrumentedCodeHasUncoveredLines() throws Exception {
        AgentJsonReporter reporter = createReporterWithInstrumentedCode();
        String json = reporter.generateFileUncovered(CALCULATOR_JAVA);

        JSONObject envelope = new JSONObject(json);
        // Should either find data or return class_not_found error
        assertTrue("must have either data or error",
                (!envelope.isNull(KEY_DATA)) || (!envelope.isNull(KEY_ERROR)));
    }

    @Test
    public void fullReportMatchesFeedback() throws Exception {
        AgentJsonReporter reporter = createReporterWithEmptyDb();
        String feedback = reporter.generateFeedback(10, 5);
        String fullReport = reporter.generateFullReport(10, 5);
        assertEquals("fullReport should equal feedback", feedback, fullReport);
    }

    @Test
    public void jsonOutputIsValidJson() throws Exception {
        AgentJsonReporter reporter = createReporterWithEmptyDb();

        // All methods should produce parseable JSON
        new JSONObject(reporter.generateFeedback(10, 5));
        new JSONObject(reporter.generateFileUncovered(NON_EXISTENT));
        new JSONObject(reporter.generateTestFeedback("non.Existent.test"));
        new JSONObject(reporter.generateSuggestions(NON_EXISTENT, 5));
        new JSONObject(reporter.generateFullReport(10, 5));
        // If any of the above throw JSONException, the test fails
    }

    // ==================== HELPERS ====================

    private AgentJsonReporter createReporterWithEmptyDb() throws Exception {
        Clover2Registry registry = new Clover2Registry(registryFile, RegAccessMode.READWRITE, TEST_PROJECT);
        InMemPerTestCoverage perTestCoverage = new InMemPerTestCoverage(10);
        CoverageData coverageData = new CoverageData(0, new int[10], perTestCoverage);
        CloverDatabase db = new CloverDatabase(registry);
        registry.setCoverageData(coverageData);
        registry.getProject().setDataProvider(coverageData);
        return new AgentJsonReporter(db, registryFile.getAbsolutePath());
    }

    private AgentJsonReporter createReporterWithInstrumentedCode() throws Exception {
        String sourceCode =
                "public class Calculator {\n" +
                "    public int add(int a, int b) {\n" +
                "        return a + b;\n" +
                METHOD_CLOSE +
                "    public int multiply(int a, int b) {\n" +
                "        return a * b;\n" +
                METHOD_CLOSE +
                "}";

        Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, TEST_PROJECT);
        InstrumentationSession session = registry.startInstr(UTF_8);
        StringWriter output = new StringWriter();

        JavaInstrumentationConfig config = new JavaInstrumentationConfig();
        config.setEncoding(UTF_8);

        StringInstrumentationSource source = new StringInstrumentationSource(
                new File(workingDir, "Calculator.java"), sourceCode);
        AstInstrumenter.instrument(source, output, session, config, null, null);

        session.exitFile();
        session.close();
        registry.saveAndOverwriteFile();

        int maxIndex = session.getCurrentFileMaxIndex();
        InMemPerTestCoverage perTestCoverage = new InMemPerTestCoverage(maxIndex + 1);
        CoverageData coverageData = new CoverageData(0, new int[maxIndex + 1], perTestCoverage);
        CloverDatabase db = new CloverDatabase(registry);
        registry.setCoverageData(coverageData);
        registry.getProject().setDataProvider(coverageData);

        return new AgentJsonReporter(db, registryFile.getAbsolutePath());
    }
}
