package org.openclover.core.reporters.agent;

import org.openclover.core.CloverDatabase;
import org.openclover.core.api.registry.FileInfo;
import org.openclover.core.api.registry.PackageInfo;
import org.openclover.core.api.registry.ProjectInfo;
import org.openclover.core.reporters.json.JSONArray;
import org.openclover.core.reporters.json.JSONException;
import org.openclover.core.reporters.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * Generates JSON coverage reports for AI agent consumption.
 *
 * All output follows the clover-agent-v1 schema with a common envelope:
 * {@code {"format":"clover-agent-v1", "scope":..., "stale":..., "dbTimestamp":..., "dbPath":..., "error":null, "data":{...}}}
 *
 * @see AgentCoverageQuery for the underlying query API
 */
public class AgentJsonReporter {

    private static final String FORMAT = "clover-agent-v1";
    private static final String SCOPE_FULL = "full-suite";
    private static final String SCOPE_SINGLE = "single-test";
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
    private static final String KEY_SUGGESTIONS = "suggestions";
    private static final String KEY_APP = "app";
    private static final String KEY_TEST_SECTION = "test";
    private static final String KEY_COMBINED = "combined";
    private static final String KEY_FILE = "file";
    private static final String KEY_STATUS = "status";
    private static final String KEY_DURATION_MS = "durationMs";
    private static final String KEY_LINES_COVERED = "linesCovered";
    private static final String KEY_UNCOVERED_LINES = "uncoveredLines";
    private static final String KEY_UNCOVERED_BRANCHES = "uncoveredBranches";
    private static final String KEY_COVERAGE = "coverage";
    private static final String KEY_METHOD = "method";
    private static final String KEY_LINE = "line";
    private static final String KEY_TYPE = "type";
    private static final String KEY_NAME = "name";
    private static final String KEY_COVERED_BY = "coveredBy";
    private static final String KEY_UNIQUE_COVERAGE = "uniqueCoverage";
    private static final String KEY_TEST = "test";
    private static final String KEY_COVERED = "covered";
    private static final String KEY_TOTAL = "total";
    private static final String KEY_PCT = "pct";
    private static final String KEY_ERROR_CODE = "code";
    private static final String KEY_ERROR_MSG = "message";
    private static final String KEY_STATEMENTS = "statements";
    private static final String KEY_BRANCHES = "branches";
    private static final String KEY_METHODS = "methods";
    private static final String KEY_TOTAL_LINES = "totalLines";
    private static final String KEY_BRANCHES_COVERED = "branchesCovered";
    private static final String KEY_UNIQUE_LINES_COVERED = "uniqueLinesCovered";
    private static final String KEY_RANK = "rank";
    private static final String KEY_COMPLEXITY = "complexity";
    private static final String KEY_COVERED_PCT = "coveredPct";
    private static final String KEY_QUICK_WINS = "quickWins";
    private static final String KEY_UNCOVERED_METHODS = "uncoveredMethods";
    private static final String KEY_EXISTING_TESTS = "existingTests";
    private static final String KEY_HAS_EXISTING_TESTS = "hasExistingTests";
    private static final String KEY_BRANCHES_ON_COVERED_LINES = "branchesOnCoveredLines";
    private static final String KEY_AVG_COMPLEXITY = "avgComplexity";
    private static final String KEY_LIKELY_TESTABLE = "likelyTestable";
    private static final String KEY_QUICK_WIN_SCORE = "quickWinScore";
    private static final String ERR_CLASS_NOT_FOUND = "class_not_found";
    private static final String ERR_TEST_NOT_FOUND = "test_not_found";
    private static final String MSG_NO_COVERAGE_FOR = "No coverage data found for: ";
    private static final long STALE_THRESHOLD_MS = 24L * 60 * 60 * 1000;

    private final CloverDatabase database;
    private final AgentCoverageQuery query;
    private final String dbPath;

    public AgentJsonReporter(CloverDatabase database, String dbPath) {
        this.database = database;
        this.query = new AgentCoverageQuery(database);
        this.dbPath = dbPath;
    }

    // ==================== FEEDBACK FILE ====================

    /**
     * Generate the auto-feedback file (Output Type 1).
     * Contains: project summary + all tests + top uncovered files.
     *
     * @param maxTests max number of tests to include (default 50)
     * @param maxFiles max number of uncovered files to include (default 10)
     * @return JSON string
     */
    public String generateFeedback(int maxTests, int maxFiles) {
        try {
            JSONObject data = new JSONObject();

            SplitCoverageSummary split = query.getProjectSummarySplit();
            JSONObject summaryObj = new JSONObject();
            summaryObj.put(KEY_APP, serializeMetrics(split.app));
            summaryObj.put(KEY_TEST_SECTION, serializeMetrics(split.test));
            summaryObj.put(KEY_COMBINED, serializeMetrics(split.combined));
            data.put(KEY_SUMMARY, summaryObj);

            // Tests sorted by unique coverage (most valuable first)
            List<TestSummary> tests = query.getAllTestsSorted();
            JSONArray testsArray = new JSONArray();
            int testCount = 0;
            for (TestSummary test : tests) {
                if (testCount >= maxTests) break;
                testsArray.put(serializeTestSummary(test));
                testCount++;
            }
            data.put(KEY_TESTS, testsArray);

            // Top uncovered: enriched with method breakdown, existing tests, quick-win signals
            List<EnrichedFileUncovered> enrichedFiles = query.getEnrichedUncoveredFiles(maxFiles);
            JSONArray uncoveredArray = new JSONArray();
            for (EnrichedFileUncovered file : enrichedFiles) {
                uncoveredArray.put(serializeEnrichedFile(file));
            }
            data.put(KEY_TOP_UNCOVERED, uncoveredArray);

            // Quick wins: best ROI targets (partially covered, branches on covered lines, has tests)
            List<EnrichedFileUncovered> quickWins = query.getQuickWins(maxFiles);
            JSONArray quickWinsArray = new JSONArray();
            for (EnrichedFileUncovered file : quickWins) {
                quickWinsArray.put(serializeEnrichedFile(file));
            }
            data.put(KEY_QUICK_WINS, quickWinsArray);

            return wrapEnvelope(SCOPE_FULL, data).toString(2);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to generate feedback JSON", e);
        }
    }

    // ==================== PER-FILE UNCOVERED ====================

    /**
     * Generate per-file uncovered report (Output Type 2).
     *
     * @param classNameOrPath class name or path to query
     * @return JSON string, or error envelope if class not found
     */
    public String generateFileUncovered(String classNameOrPath) {
        try {
            FileUncoveredResult result = query.getFileUncovered(classNameOrPath);
            if (result == null) {
                return wrapError(ERR_CLASS_NOT_FOUND,
                        MSG_NO_COVERAGE_FOR + classNameOrPath).toString(2);
            }
            return wrapEnvelope(SCOPE_FULL, serializeFileUncoveredFull(result)).toString(2);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to generate file uncovered JSON", e);
        }
    }

    // ==================== TEST FEEDBACK ====================

    /**
     * Generate per-test feedback report (Output Type 3).
     *
     * @param testQualifiedName qualified test name
     * @return JSON string, or error envelope if test not found
     */
    public String generateTestFeedback(String testQualifiedName) {
        try {
            TestCoverageResult result = query.getTestCoverage(testQualifiedName);
            if (result == null) {
                return wrapError(ERR_TEST_NOT_FOUND,
                        MSG_NO_COVERAGE_FOR + testQualifiedName).toString(2);
            }
            return wrapEnvelope(SCOPE_SINGLE, serializeTestCoverage(result)).toString(2);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to generate test feedback JSON", e);
        }
    }

    // ==================== SUGGEST ====================

    /**
     * Generate test suggestions (Output Type 4).
     *
     * @param classNameOrPath class name or path
     * @param maxSuggestions max suggestions to return
     * @return JSON string
     */
    public String generateSuggestions(String classNameOrPath, int maxSuggestions) {
        try {
            FileUncoveredResult fileResult = query.getFileUncovered(classNameOrPath);
            if (fileResult == null) {
                return wrapError(ERR_CLASS_NOT_FOUND,
                        MSG_NO_COVERAGE_FOR + classNameOrPath).toString(2);
            }

            JSONObject data = new JSONObject();
            data.put(KEY_FILE, fileResult.file);

            JSONArray suggestions = new JSONArray();
            List<MethodSuggestion> methods = query.getMethodSuggestions(classNameOrPath, maxSuggestions);
            for (MethodSuggestion ms : methods) {
                suggestions.put(serializeMethodSuggestion(ms));
            }
            data.put(KEY_SUGGESTIONS, suggestions);

            return wrapEnvelope(SCOPE_FULL, data).toString(2);
        } catch (JSONException e) {
            throw new RuntimeException("Failed to generate suggestions JSON", e);
        }
    }

    // ==================== FULL REPORT ====================

    /**
     * Generate full agent report (Output Type 5).
     * Combines project summary + tests + per-file uncovered.
     *
     * @param maxTests max tests to include
     * @param maxFiles max uncovered files to include
     * @return JSON string
     */
    public String generateFullReport(int maxTests, int maxFiles) {
        return generateFeedback(maxTests, maxFiles);
    }

    // ==================== FILE OUTPUT ====================

    /**
     * Write a JSON report to a file.
     *
     * @param json JSON content
     * @param outputFile target file
     * @throws IOException if write fails
     */
    public static void writeToFile(String json, File outputFile) throws IOException {
        outputFile.getParentFile().mkdirs();
        Files.write(outputFile.toPath(), json.getBytes(StandardCharsets.UTF_8));
    }

    // ==================== ENVELOPE ====================

    private JSONObject wrapEnvelope(String scope, JSONObject data) throws JSONException {
        JSONObject envelope = new JSONObject();
        envelope.put(KEY_FORMAT, FORMAT);
        envelope.put(KEY_SCOPE, scope);
        envelope.put(KEY_STALE, isStale());
        envelope.put(KEY_DB_TIMESTAMP, formatTimestamp(getDbTimestamp()));
        envelope.put(KEY_DB_PATH, dbPath);
        envelope.put(KEY_ERROR, JSONObject.NULL);
        envelope.put(KEY_DATA, data);
        return envelope;
    }

    private JSONObject wrapError(String code, String message) throws JSONException {
        JSONObject envelope = new JSONObject();
        envelope.put(KEY_FORMAT, FORMAT);
        envelope.put(KEY_SCOPE, SCOPE_FULL);
        envelope.put(KEY_STALE, false);
        envelope.put(KEY_DB_TIMESTAMP, formatTimestamp(getDbTimestamp()));
        envelope.put(KEY_DB_PATH, dbPath);

        JSONObject error = new JSONObject();
        error.put(KEY_ERROR_CODE, code);
        error.put(KEY_ERROR_MSG, message);
        envelope.put(KEY_ERROR, error);
        envelope.put(KEY_DATA, JSONObject.NULL);
        return envelope;
    }

    // ==================== SERIALIZATION ====================

    private JSONObject serializeMetrics(CoverageMetrics metrics) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_STATEMENTS, serializeMetricPair(metrics.statements));
        obj.put(KEY_BRANCHES, serializeMetricPair(metrics.branches));
        obj.put(KEY_METHODS, serializeMetricPair(metrics.methods));
        return obj;
    }

    private JSONObject serializeEnrichedFile(EnrichedFileUncovered file) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_FILE, file.file);
        obj.put(KEY_COVERAGE, serializeMetrics(file.coverage));
        obj.put(KEY_UNCOVERED_LINES, new JSONArray(file.uncoveredLines));
        obj.put(KEY_UNCOVERED_BRANCHES, serializeBranches(file.uncoveredBranches));

        // Method-level breakdown — eliminates need for clover:suggest follow-up
        JSONArray methodsArray = new JSONArray();
        for (MethodSuggestion ms : file.uncoveredMethods) {
            methodsArray.put(serializeMethodSuggestion(ms));
        }
        obj.put(KEY_UNCOVERED_METHODS, methodsArray);

        // Existing test classes — eliminates need for clover:uncovered follow-up
        obj.put(KEY_EXISTING_TESTS, new JSONArray(file.existingTests));
        obj.put(KEY_HAS_EXISTING_TESTS, file.hasExistingTests);

        // Quick-win signals
        obj.put(KEY_BRANCHES_ON_COVERED_LINES, file.branchesOnCoveredLines);
        obj.put(KEY_AVG_COMPLEXITY, Math.round(file.avgComplexity * 10.0) / 10.0);
        obj.put(KEY_LIKELY_TESTABLE, file.likelyTestable);
        obj.put(KEY_QUICK_WIN_SCORE, Math.round(file.quickWinScore * 10.0) / 10.0);

        return obj;
    }

    private JSONObject serializeMetricPair(MetricPair pair) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_COVERED, pair.covered);
        obj.put(KEY_TOTAL, pair.total);
        obj.put(KEY_PCT, Math.round(pair.pct * 10.0) / 10.0);
        return obj;
    }

    private JSONObject serializeTestSummary(TestSummary test) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_NAME, test.name);
        obj.put(KEY_STATUS, test.status);
        obj.put(KEY_DURATION_MS, test.durationMs);
        obj.put(KEY_LINES_COVERED, test.linesCovered);
        obj.put(KEY_UNIQUE_LINES_COVERED, test.uniqueLinesCovered);
        return obj;
    }

    private JSONObject serializeFileUncoveredCompact(FileUncoveredResult file) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_FILE, file.file);
        obj.put(KEY_UNCOVERED_LINES, new JSONArray(file.uncoveredLines));
        obj.put(KEY_UNCOVERED_BRANCHES, serializeBranches(file.uncoveredBranches));
        return obj;
    }

    private JSONObject serializeFileUncoveredFull(FileUncoveredResult file) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_FILE, file.file);
        obj.put(KEY_TOTAL_LINES, file.totalLines);
        obj.put(KEY_COVERAGE, serializeMetrics(file.coverage));
        obj.put(KEY_UNCOVERED_LINES, new JSONArray(file.uncoveredLines));
        obj.put(KEY_UNCOVERED_BRANCHES, serializeBranches(file.uncoveredBranches));

        JSONObject coveredByObj = new JSONObject();
        for (Map.Entry<String, List<String>> entry : file.coveredBy.entrySet()) {
            coveredByObj.put(entry.getKey(), new JSONArray(entry.getValue()));
        }
        obj.put(KEY_COVERED_BY, coveredByObj);

        return obj;
    }

    private JSONObject serializeTestCoverage(TestCoverageResult result) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_TEST, result.testName);
        obj.put(KEY_STATUS, result.status);
        obj.put(KEY_DURATION_MS, result.durationMs);

        JSONObject coverageObj = new JSONObject();
        for (Map.Entry<String, FileCoverage> entry : result.coverage.entrySet()) {
            JSONObject fileCov = new JSONObject();
            fileCov.put(KEY_LINES_COVERED, new JSONArray(entry.getValue().linesCovered));
            fileCov.put(KEY_BRANCHES_COVERED, serializeBranchCoverages(entry.getValue().branchesCovered));
            coverageObj.put(entry.getKey(), fileCov);
        }
        obj.put(KEY_COVERAGE, coverageObj);

        if (result.uniqueCoverage != null) {
            JSONObject uniqueCoverageObj = new JSONObject();
            for (Map.Entry<String, List<Integer>> entry : result.uniqueCoverage.entrySet()) {
                uniqueCoverageObj.put(entry.getKey(), new JSONArray(entry.getValue()));
            }
            obj.put(KEY_UNIQUE_COVERAGE, uniqueCoverageObj);
        }

        return obj;
    }

    private JSONObject serializeMethodSuggestion(MethodSuggestion ms) throws JSONException {
        JSONObject obj = new JSONObject();
        obj.put(KEY_RANK, ms.rank);
        obj.put(KEY_METHOD, ms.method);
        obj.put(KEY_UNCOVERED_LINES, new JSONArray(ms.uncoveredLines));
        obj.put(KEY_UNCOVERED_BRANCHES, serializeBranches(ms.uncoveredBranches));
        obj.put(KEY_COMPLEXITY, ms.complexity);
        obj.put(KEY_COVERED_PCT, Math.round(ms.coveredPct * 10.0) / 10.0);
        return obj;
    }

    private JSONArray serializeBranches(List<UncoveredBranch> branches) throws JSONException {
        JSONArray arr = new JSONArray();
        for (UncoveredBranch branch : branches) {
            JSONObject b = new JSONObject();
            b.put(KEY_LINE, branch.line);
            b.put(KEY_TYPE, branch.type);
            if (branch.method != null) {
                b.put(KEY_METHOD, branch.method);
            }
            arr.put(b);
        }
        return arr;
    }

    private JSONArray serializeBranchCoverages(List<BranchCoverage> branches) throws JSONException {
        JSONArray arr = new JSONArray();
        for (BranchCoverage branch : branches) {
            JSONObject b = new JSONObject();
            b.put(KEY_LINE, branch.line);
            b.put(KEY_TYPE, branch.type);
            arr.put(b);
        }
        return arr;
    }

    // ==================== HELPERS ====================

    private List<FileUncoveredResult> getAllUncoveredFiles() {
        List<FileUncoveredResult> results = new ArrayList<>();
        ProjectInfo model = database.getFullModel();
        if (model == null) return results;

        for (PackageInfo pkg : model.getAllPackages()) {
            for (FileInfo file : pkg.getFiles()) {
                String path = getPackagePath(file);
                FileUncoveredResult result = query.getFileUncovered(path);
                if (result != null) {
                    results.add(result);
                }
            }
        }

        results.sort((a, b) -> Integer.compare(b.uncoveredLines.size(), a.uncoveredLines.size()));
        return results;
    }

    private String getPackagePath(FileInfo file) {
        if (file.getPhysicalFile() != null) {
            return file.getPhysicalFile().getName();
        }
        return file.getName();
    }

    private boolean isStale() {
        // Check if any source file has been modified since the DB was written
        File dbFile = new File(dbPath);
        if (!dbFile.exists()) return true;
        long dbTime = dbFile.lastModified();
        // Simplified: only check DB exists and is recent
        return System.currentTimeMillis() - dbTime > STALE_THRESHOLD_MS;
    }

    private long getDbTimestamp() {
        File dbFile = new File(dbPath);
        return dbFile.exists() ? dbFile.lastModified() : System.currentTimeMillis();
    }

    private static String formatTimestamp(long millis) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date(millis));
    }
}
