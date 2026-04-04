package org.openclover.core.reporters.agent;

import org.openclover.core.CloverDatabase;
import org.openclover.core.CoverageData;
import org.openclover.core.api.registry.TestCaseInfo;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.List;

/**
 * Scores tests by their unique coverage contribution.
 *
 * A test's unique coverage = lines/branches it covers that NO other test covers.
 * Tests with high unique coverage are essential (removing them loses coverage).
 * Tests with zero unique coverage are candidates for redundancy detection.
 *
 * @see TestRedundancyDetector for identifying zero-unique-coverage tests
 */
public class CoverageUniquenessScorer {

    private final CloverDatabase database;

    public CoverageUniquenessScorer(CloverDatabase database) {
        this.database = database;
    }

    /**
     * Score all tests by unique line coverage.
     *
     * @return tests sorted by unique coverage descending (most essential first)
     */
    public List<TestUniquenessScore> scoreAllTests() {
        CoverageData coverageData = database.getCoverageData();
        if (coverageData == null) {
            return Collections.emptyList();
        }

        List<TestUniquenessScore> scores = new ArrayList<>();

        for (TestCaseInfo tci : coverageData.getTests()) {
            BitSet hits = coverageData.getHitsFor(tci);
            BitSet uniqueHits = coverageData.getUniqueHitsFor(tci);

            TestUniquenessScore score = new TestUniquenessScore();
            score.testName = tci.getQualifiedName();
            score.totalLinesCovered = hits != null ? hits.cardinality() : 0;
            score.uniqueLinesCovered = uniqueHits != null ? uniqueHits.cardinality() : 0;
            score.uniquenessRatio = score.totalLinesCovered > 0
                    ? (double) score.uniqueLinesCovered / score.totalLinesCovered
                    : 0.0;
            scores.add(score);
        }

        scores.sort((a, b) -> Integer.compare(b.uniqueLinesCovered, a.uniqueLinesCovered));
        return scores;
    }

    /**
     * Uniqueness score for a single test.
     */
    public static class TestUniquenessScore {
        public String testName;
        public int totalLinesCovered;
        public int uniqueLinesCovered;
        public double uniquenessRatio;
    }
}
