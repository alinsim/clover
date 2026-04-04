package org.openclover.core.reporters.agent;

import org.openclover.core.CloverDatabase;
import org.openclover.core.CoverageData;
import org.openclover.core.api.registry.ProjectInfo;
import org.openclover.core.api.registry.TestCaseInfo;
import org.openclover.core.registry.metrics.BlockMetrics;

import java.util.BitSet;

/**
 * Computes coverage delta: what a single test contributes to overall coverage.
 * "Before" = coverage from all tests EXCEPT this one.
 * "After" = coverage from all tests INCLUDING this one.
 * Delta = after - before.
 */
public class CoverageDiffGenerator {

    private final CloverDatabase database;

    public CoverageDiffGenerator(CloverDatabase database) {
        this.database = database;
    }

    /**
     * Compute the coverage delta for a specific test.
     *
     * @param testQualifiedName fully qualified test name
     * @return delta result, or null if test not found
     */
    public CoverageDelta computeDelta(String testQualifiedName) {
        CoverageData coverageData = database.getCoverageData();
        if (coverageData == null) {
            return null;
        }

        TestCaseInfo targetTest = findTest(coverageData, testQualifiedName);
        if (targetTest == null) {
            return null;
        }

        // "After" = full project metrics (all tests including this one)
        ProjectInfo fullModel = database.getFullModel();
        if (fullModel == null) {
            return null;
        }
        BlockMetrics afterMetrics = (BlockMetrics) fullModel.getMetrics();

        // Compute unique hits for this test
        BitSet uniqueHits = coverageData.getUniqueHitsFor(targetTest);
        int uniqueStatements = uniqueHits != null ? uniqueHits.cardinality() : 0;

        // "Before" = after minus unique contribution
        double afterStmtPct = afterMetrics.getNumStatements() > 0
                ? afterMetrics.getNumCoveredStatements() * 100.0 / afterMetrics.getNumStatements()
                : 0.0;
        double beforeStmtPct = afterMetrics.getNumStatements() > 0
                ? (afterMetrics.getNumCoveredStatements() - uniqueStatements) * 100.0 / afterMetrics.getNumStatements()
                : 0.0;

        double afterBranchPct = afterMetrics.getNumBranches() > 0
                ? afterMetrics.getNumCoveredBranches() * 100.0 / afterMetrics.getNumBranches()
                : 0.0;
        // Approximate branch delta from unique hits (conservative estimate)
        double beforeBranchPct = afterBranchPct;

        CoverageDelta delta = new CoverageDelta();
        delta.testName = testQualifiedName;
        delta.statements = new DeltaPair();
        delta.statements.before = Math.round(beforeStmtPct * 10.0) / 10.0;
        delta.statements.after = Math.round(afterStmtPct * 10.0) / 10.0;
        delta.statements.delta = Math.round((afterStmtPct - beforeStmtPct) * 10.0) / 10.0;

        delta.branches = new DeltaPair();
        delta.branches.before = Math.round(beforeBranchPct * 10.0) / 10.0;
        delta.branches.after = Math.round(afterBranchPct * 10.0) / 10.0;
        delta.branches.delta = Math.round((afterBranchPct - beforeBranchPct) * 10.0) / 10.0;

        return delta;
    }

    private TestCaseInfo findTest(CoverageData coverageData, String qualifiedName) {
        for (TestCaseInfo tci : coverageData.getTests()) {
            if (tci.getQualifiedName() != null && tci.getQualifiedName().equals(qualifiedName)) {
                return tci;
            }
        }
        return null;
    }

    /**
     * Coverage delta result for a single test.
     */
    public static class CoverageDelta {
        public String testName;
        public DeltaPair statements;
        public DeltaPair branches;
    }

    /**
     * Before/after/delta triple for a coverage metric.
     */
    public static class DeltaPair {
        public double before;
        public double after;
        public double delta;
    }
}
