package org.openclover.core.reporters.agent;

import java.util.List;

/**
 * Method suggestion for test target.
 */
public class MethodSuggestion {
    public int rank;
    public String method;
    public List<Integer> uncoveredLines;
    public List<UncoveredBranch> uncoveredBranches;
    public int complexity;
    public double coveredPct;
}
