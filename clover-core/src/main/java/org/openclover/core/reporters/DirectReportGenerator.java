package org.openclover.core.reporters;

import org.openclover.core.CloverDatabase;
import org.openclover.runtime.Logger;
import org.openclover.runtime.api.CloverException;

import java.io.File;

/**
 * Direct report generator that bypasses Ant entirely.
 * Constructs {@link Current} config and calls {@link CloverReporter} directly.
 */
public class DirectReportGenerator {

    /**
     * Result of report generation.
     */
    public static class Result {
        private final boolean success;
        private final int testCount;
        private final String errorMessage;

        private Result(boolean success, int testCount, String errorMessage) {
            this.success = success;
            this.testCount = testCount;
            this.errorMessage = errorMessage;
        }

        static Result success(int testCount) {
            return new Result(true, testCount, null);
        }

        static Result failure(String errorMessage) {
            return new Result(false, 0, errorMessage);
        }

        public boolean isSuccess() {
            return success;
        }

        public int getTestCount() {
            return testCount;
        }

        public String getErrorMessage() {
            return errorMessage;
        }
    }

    /**
     * Generates an HTML report directly from a Clover database.
     *
     * @param dbPath    path to clover.db
     * @param outputDir directory for HTML output
     * @return result with success/failure and test count
     */
    public Result generateHtmlReport(String dbPath, File outputDir) {
        return generateReport(dbPath, outputDir, Format.DEFAULT_HTML);
    }

    /**
     * Generates an XML report directly from a Clover database.
     *
     * @param dbPath  path to clover.db
     * @param xmlFile output XML file
     * @return result with success/failure and test count
     */
    public Result generateXmlReport(String dbPath, File xmlFile) {
        return generateReport(dbPath, xmlFile, Format.DEFAULT_XML);
    }

    private Result generateReport(String dbPath, File outFile, Format format) {
        if (!new File(dbPath).exists()) {
            return Result.failure("Database not found: " + dbPath);
        }

        try {
            Current config = new Current();
            config.setInitString(dbPath);
            config.setFormat(format);
            config.setOutFile(outFile);
            config.setAlwaysReport(true);
            config.setTitle("Coverage Report");
            config.setMainFileName("index.html");

            CloverDatabase db = config.getCoverageDatabase();
            if (db == null) {
                return Result.failure("Failed to load coverage database");
            }

            int testCount = db.getCoverageData().getTests().size();

            CloverReporter reporter = CloverReporter.buildReporter(config);
            reporter.execute();

            return Result.success(testCount);
        } catch (CloverException e) {
            Logger.getInstance().error("Report generation failed: " + e.getMessage(), e);
            return Result.failure(e.getMessage());
        }
    }
}
