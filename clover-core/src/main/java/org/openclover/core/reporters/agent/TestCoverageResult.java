package org.openclover.core.reporters.agent;

import java.util.List;
import java.util.Map;

/**
 * Result object for per-test coverage query.
 */
public class TestCoverageResult {
    public String testName;
    public String status;
    public long durationMs;
    public Map<String, FileCoverage> coverage;
    public Map<String, List<Integer>> uniqueCoverage;
}
