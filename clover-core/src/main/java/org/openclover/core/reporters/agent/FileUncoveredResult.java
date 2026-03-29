package org.openclover.core.reporters.agent;

import java.util.List;
import java.util.Map;

/**
 * Result object for per-file uncovered query.
 */
public class FileUncoveredResult {
    public String file;
    public int totalLines;
    public CoverageMetrics coverage;
    public List<Integer> uncoveredLines;
    public List<UncoveredBranch> uncoveredBranches;
    public Map<String, List<String>> coveredBy;
}
