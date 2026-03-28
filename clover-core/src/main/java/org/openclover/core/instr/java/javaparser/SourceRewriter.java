package org.openclover.core.instr.java.javaparser;

import org_openclover_runtime.CloverVersionInfo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Rewrites Java source code by applying text insertions at specific positions.
 * <p>
 * This class takes original source code and a list of {@link Insertion} objects,
 * and produces modified source code with all insertions applied. Insertions are
 * applied in reverse order (from end to start) to avoid position shifts.
 * </p>
 * <p>
 * This is the output stage of the JavaParser-based instrumentation pipeline,
 * replacing ANTLR's token stream rewriting.
 * </p>
 */
public class SourceRewriter {

    /**
     * The marker prefix used to identify instrumented files.
     * Public to allow double instrumentation detection.
     */
    public static final String MARKER_PREFIX = "/* $$ This file has been instrumented by OpenClover ";
    private static final String MARKER_SUFFIX = " $$ */";

    /**
     * The instrumentation marker, built from the OpenClover version info.
     */
    public static final String MARKER = MARKER_PREFIX
            + CloverVersionInfo.RELEASE_NUM + "#" + CloverVersionInfo.BUILD_STAMP
            + MARKER_SUFFIX;

    /**
     * Applies insertions to the source code.
     * <p>
     * Insertions are sorted in reverse order (line DESC, column DESC) and applied
     * from bottom to top, right to left. This ensures that earlier insertions don't
     * shift the positions of later ones.
     * </p>
     * <p>
     * The algorithm converts line/column positions to character offsets in the source
     * string, then splices in the insertion text at the appropriate offset.
     * </p>
     *
     * @param originalSource the original source code
     * @param insertions     list of insertions to apply
     * @return the rewritten source code with all insertions applied
     */
    public static String rewrite(String originalSource, List<Insertion> insertions) {
        if (insertions == null || insertions.isEmpty()) {
            return originalSource;
        }

        // Sort insertions in reverse order (line DESC, column DESC, order ASC)
        List<Insertion> sortedInsertions = new ArrayList<>(insertions);
        Collections.sort(sortedInsertions);

        // Apply insertions to a StringBuilder for efficiency
        StringBuilder result = new StringBuilder(originalSource);

        for (Insertion insertion : sortedInsertions) {
            int offset = computeCharacterOffset(originalSource, insertion.getLine(), insertion.getColumn());

            if (offset >= 0 && offset <= result.length()) {
                // For AFTER insertions, move offset past the character at the position
                if (insertion.getType() == Insertion.InsertionPoint.AFTER && offset < result.length()) {
                    offset++;
                }

                result.insert(offset, insertion.getText());
            }
        }

        return result.toString();
    }

    /**
     * Adds the OpenClover instrumentation marker to the beginning of the source code.
     * <p>
     * The marker is a comment that identifies the file as instrumented by OpenClover,
     * including the version and build stamp. This helps detect double instrumentation.
     * </p>
     *
     * @param source the source code
     * @return the source code with the marker prepended
     */
    public static String addMarker(String source) {
        return MARKER + source;
    }

    /**
     * Computes the character offset in the source string for a given line and column.
     * <p>
     * Line and column numbers are 1-based (as used in source positions). The method
     * handles mixed line endings (\r\n, \n, \r) correctly.
     * </p>
     *
     * @param source the source code
     * @param line   1-based line number
     * @param column 1-based column number
     * @return the 0-based character offset, or -1 if the position is invalid
     */
    private static int computeCharacterOffset(String source, int line, int column) {
        if (line < 1 || column < 1) {
            return -1;
        }

        int currentLine = 1;
        int offset = 0;

        // Scan to the target line
        while (offset < source.length() && currentLine < line) {
            char c = source.charAt(offset);

            // Handle different line endings
            if (c == '\r') {
                // Check for \r\n (DOS line ending)
                if (offset + 1 < source.length() && source.charAt(offset + 1) == '\n') {
                    offset += 2; // Skip both \r and \n
                } else {
                    offset++; // Just \r (Mac classic)
                }
                currentLine++;
            } else if (c == '\n') {
                offset++; // Unix line ending
                currentLine++;
            } else {
                offset++;
            }
        }

        // If we haven't reached the target line, position is invalid
        if (currentLine < line) {
            return -1;
        }

        // Now advance to the target column on the current line
        int currentColumn = 1;
        while (offset < source.length() && currentColumn < column) {
            char c = source.charAt(offset);

            // Stop at line ending
            if (c == '\r' || c == '\n') {
                break;
            }

            offset++;
            currentColumn++;
        }

        // If we haven't reached the target column, position is at end of line
        // This is valid (can insert at end of line)
        return offset;
    }
}
