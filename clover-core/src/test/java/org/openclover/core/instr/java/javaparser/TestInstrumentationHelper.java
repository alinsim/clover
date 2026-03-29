package org.openclover.core.instr.java.javaparser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper for branch instrumentation tests.
 * Instruments source code strings and provides utilities to inspect the output.
 */
public final class TestInstrumentationHelper {

    private static final Pattern INC_PATTERN = Pattern.compile("\\.inc\\((\\d+)\\)");
    private static final String RECORDER_PREFIX = "__CLR_TEST";
    private static final String INIT_STRING = "/tmp/test-clover.db";

    private TestInstrumentationHelper() {}

    /**
     * Instrument a Java source string using the standalone JavaParser instrumenter.
     */
    public static String instrument(String source) {
        return JavaParserInstrumenter.instrument(source, RECORDER_PREFIX, INIT_STRING, 0L);
    }

    /**
     * Extract ALL R.inc(N) indices from the instrumented source, in order of appearance.
     */
    public static List<Integer> extractAllIndices(String instrumented) {
        List<Integer> indices = new ArrayList<>();
        Matcher matcher = INC_PATTERN.matcher(instrumented);
        while (matcher.find()) {
            indices.add(Integer.parseInt(matcher.group(1)));
        }
        return indices;
    }

    /**
     * Extract the first R.inc(N) index that appears before the given marker text.
     * Searches within 200 chars before the marker.
     */
    public static int extractFirstBranchIndex(String instrumented, String nearMarker) {
        int markerPos = instrumented.indexOf(nearMarker);
        if (markerPos < 0) {
            return -1;
        }

        String before = instrumented.substring(Math.max(0, markerPos - 200), markerPos);
        Matcher matcher = INC_PATTERN.matcher(before);
        int lastIndex = -1;
        while (matcher.find()) {
            lastIndex = Integer.parseInt(matcher.group(1));
        }
        return lastIndex;
    }

    /**
     * Check if there is an R.inc() call before the given marker text.
     */
    public static boolean hasBranchIncBefore(String instrumented, String marker) {
        return extractFirstBranchIndex(instrumented, marker) >= 0;
    }

    /**
     * Find a consecutive pair (N, N+1) in the list of indices.
     * Returns the first such N, or -1 if not found.
     */
    public static int findConsecutivePair(List<Integer> indices) {
        for (int i = 0; i < indices.size() - 1; i++) {
            for (int j = i + 1; j < indices.size(); j++) {
                if (indices.get(j) == indices.get(i) + 1) {
                    return indices.get(i);
                }
            }
        }
        return -1;
    }
}
