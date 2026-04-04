package org.openclover.core.reporters.agent;

import java.util.List;

/**
 * Enriched version of FileUncoveredResult for the feedback file.
 * Contains everything an agent needs to act on a file without follow-up queries:
 * method-level breakdown, existing test classes, testability signals, and quick-win metrics.
 */
public class EnrichedFileUncovered {
    /** Package path of the file (e.g., "com/example/OrderService.java") */
    public String file;

    /** Per-file coverage metrics */
    public CoverageMetrics coverage;

    /** Exact uncovered line numbers (deduplicated, sorted) */
    public List<Integer> uncoveredLines;

    /** Uncovered branches with line, type, method context */
    public List<UncoveredBranch> uncoveredBranches;

    /** Method-level breakdown: which methods have gaps and how big */
    public List<MethodSuggestion> uncoveredMethods;

    /** Test classes that already cover parts of this file */
    public List<String> existingTests;

    /** Whether any test class covers this file */
    public boolean hasExistingTests;

    /**
     * Number of uncovered branches on lines that ARE already covered.
     * This is the strongest quick-win signal — the code is reachable,
     * just need one more test case for the other branch path.
     */
    public int branchesOnCoveredLines;

    /** Average cyclomatic complexity of uncovered methods */
    public double avgComplexity;

    /** Testability hint: false if file contains only infrastructure methods (main, lifecycle) */
    public boolean likelyTestable;

    /**
     * Quick-win score (higher = easier/better ROI to improve coverage).
     * Factors: existing test coverage, branches on covered lines, low complexity.
     */
    public double quickWinScore;
}
