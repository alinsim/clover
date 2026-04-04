package org.openclover.core.reporters.agent;

import org.openclover.core.CloverDatabase;

import java.util.ArrayList;
import java.util.List;

/**
 * Identifies tests with zero unique coverage — tests that cover only lines
 * already covered by other tests. These are candidates for:
 * - Removal (if they test the same paths as other tests)
 * - Refactoring (if they should be testing different paths)
 * - Review (they may still have value for regression detection)
 *
 * Uses {@link CoverageUniquenessScorer} for the underlying analysis.
 */
public class TestRedundancyDetector {

    private final CoverageUniquenessScorer scorer;

    public TestRedundancyDetector(CloverDatabase database) {
        this.scorer = new CoverageUniquenessScorer(database);
    }

    /**
     * Find all tests that contribute zero unique coverage.
     *
     * @return list of redundant test names (may still have regression value)
     */
    public List<RedundantTest> findRedundantTests() {
        List<RedundantTest> redundant = new ArrayList<>();

        for (CoverageUniquenessScorer.TestUniquenessScore score : scorer.scoreAllTests()) {
            if (score.uniqueLinesCovered == 0 && score.totalLinesCovered > 0) {
                RedundantTest rt = new RedundantTest();
                rt.testName = score.testName;
                rt.totalLinesCovered = score.totalLinesCovered;
                redundant.add(rt);
            }
        }

        return redundant;
    }

    /**
     * Get a summary of test suite redundancy.
     *
     * @return summary with counts and percentages
     */
    public RedundancySummary getSummary() {
        List<CoverageUniquenessScorer.TestUniquenessScore> allScores = scorer.scoreAllTests();

        RedundancySummary summary = new RedundancySummary();
        summary.totalTests = allScores.size();
        summary.redundantTests = 0;
        summary.essentialTests = 0;

        for (CoverageUniquenessScorer.TestUniquenessScore score : allScores) {
            if (score.uniqueLinesCovered == 0 && score.totalLinesCovered > 0) {
                summary.redundantTests++;
            } else if (score.uniqueLinesCovered > 0) {
                summary.essentialTests++;
            }
        }

        summary.redundancyPct = summary.totalTests > 0
                ? summary.redundantTests * 100.0 / summary.totalTests
                : 0.0;
        return summary;
    }

    /**
     * A test with zero unique coverage.
     */
    public static class RedundantTest {
        public String testName;
        public int totalLinesCovered;
    }

    /**
     * Summary of test suite redundancy.
     */
    public static class RedundancySummary {
        public int totalTests;
        public int redundantTests;
        public int essentialTests;
        public double redundancyPct;
    }
}
