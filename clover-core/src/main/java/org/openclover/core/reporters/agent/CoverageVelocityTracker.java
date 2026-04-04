package org.openclover.core.reporters.agent;

import java.util.ArrayList;
import java.util.List;

/**
 * Tracks coverage progression across successive test runs to detect plateaus.
 * When coverage stops increasing, the agent knows to switch strategies
 * (e.g., target different methods, use different test approaches).
 *
 * Usage: call {@link #recordDataPoint} after each test run, then check
 * {@link #isPlateaued} to detect stalled coverage.
 */
public class CoverageVelocityTracker {

    private static final int DEFAULT_WINDOW = 3;
    private static final double DEFAULT_THRESHOLD = 0.5;

    private final List<DataPoint> history = new ArrayList<>();
    private final int windowSize;
    private final double plateauThreshold;

    public CoverageVelocityTracker() {
        this(DEFAULT_WINDOW, DEFAULT_THRESHOLD);
    }

    public CoverageVelocityTracker(int windowSize, double plateauThresholdPct) {
        this.windowSize = windowSize;
        this.plateauThreshold = plateauThresholdPct;
    }

    /**
     * Record a coverage data point after a test run.
     *
     * @param coveragePct overall statement coverage percentage (0-100)
     * @param testCount number of tests executed
     */
    public void recordDataPoint(double coveragePct, int testCount) {
        DataPoint dp = new DataPoint();
        dp.coveragePct = coveragePct;
        dp.testCount = testCount;
        dp.timestamp = System.currentTimeMillis();
        history.add(dp);
    }

    /**
     * Check if coverage has plateaued (no significant improvement in recent runs).
     *
     * @return true if the last N runs showed less than threshold% improvement total
     */
    public boolean isPlateaued() {
        if (history.size() < windowSize + 1) {
            return false;
        }

        int start = history.size() - windowSize - 1;
        double startPct = history.get(start).coveragePct;
        double endPct = history.get(history.size() - 1).coveragePct;

        return (endPct - startPct) < plateauThreshold;
    }

    /**
     * Get the coverage velocity (percentage points per run) over the recent window.
     *
     * @return velocity in pct points per run, or 0 if insufficient data
     */
    public double getVelocity() {
        if (history.size() < 2) {
            return 0.0;
        }

        int start = Math.max(0, history.size() - windowSize - 1);
        double startPct = history.get(start).coveragePct;
        double endPct = history.get(history.size() - 1).coveragePct;
        int runs = history.size() - 1 - start;

        return runs > 0 ? (endPct - startPct) / runs : 0.0;
    }

    public List<DataPoint> getHistory() {
        return new ArrayList<>(history);
    }

    public static class DataPoint {
        public double coveragePct;
        public int testCount;
        public long timestamp;
    }
}
