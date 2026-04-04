package org.openclover.core.reporters.agent;

/**
 * Project coverage split into app (production), test, and combined sections.
 * Prevents inflated coverage numbers from test sources covering themselves.
 */
public class SplitCoverageSummary {
    public CoverageMetrics app;
    public CoverageMetrics test;
    public CoverageMetrics combined;
}
