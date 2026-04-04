package org.openclover.core.instr.java.javaparser;

import org.openclover.core.api.instrumentation.InstrumentationSession;
import org.openclover.core.cfg.instr.java.JavaInstrumentationConfig;
import org.openclover.core.instr.java.StringInstrumentationSource;
import org.openclover.core.registry.Clover2Registry;
import org.openclover.runtime.api.CloverException;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Helper for instrumentation tests.
 * Instruments source code strings via AstInstrumenter with a minimal session
 * and provides utilities to inspect the output.
 */
public final class TestInstrumentationHelper {

    private static final Pattern INC_PATTERN = Pattern.compile("\\.inc\\((\\d+)\\)");

    private TestInstrumentationHelper() {}

    /**
     * Instrument a Java source string using AstInstrumenter with a temporary session.
     */
    public static String instrument(String source) {
        return instrumentWithSession(source, new JavaInstrumentationConfig());
    }

    /**
     * Instrument a Java source string using AstInstrumenter with a temporary session
     * and the given config.
     */
    public static String instrumentWithSession(String source, JavaInstrumentationConfig config) {
        try {
            File tempDir = Files.createTempDirectory("clover-test-helper").toFile();
            File registryFile = new File(tempDir, "clover.db");
            try {
                Clover2Registry registry = Clover2Registry.createOrLoad(registryFile, "test");
                InstrumentationSession session = registry.startInstr("UTF-8");
                StringWriter output = new StringWriter();

                StringInstrumentationSource instrSource = new StringInstrumentationSource(
                        new File("TestSource.java"), source);
                AstInstrumenter.instrument(instrSource, output, session, config, null, null);

                session.exitFile();
                session.close();
                return output.toString();
            } finally {
                registryFile.delete();
                new File(tempDir, "clover.db.prev").delete();
                tempDir.delete();
            }
        } catch (IOException | CloverException e) {
            throw new RuntimeException("Test instrumentation failed", e);
        }
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
