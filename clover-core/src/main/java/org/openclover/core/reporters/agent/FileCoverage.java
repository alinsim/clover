package org.openclover.core.reporters.agent;

import java.util.List;

/**
 * Coverage information for a single file.
 */
public class FileCoverage {
    public List<Integer> linesCovered;
    public List<BranchCoverage> branchesCovered;
}
