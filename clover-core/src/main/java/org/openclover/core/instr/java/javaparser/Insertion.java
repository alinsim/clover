package org.openclover.core.instr.java.javaparser;

import java.util.Objects;

/**
 * Represents a text insertion into Java source code at a specific position.
 * <p>
 * Each insertion specifies a location (line and column), the text to insert,
 * whether to insert before or after the position, and an order value for
 * handling multiple insertions at the same position.
 * </p>
 * <p>
 * Insertions are comparable and sort in reverse order (line DESC, column DESC)
 * to facilitate applying them from end to start, avoiding position shifts.
 * </p>
 */
public class Insertion implements Comparable<Insertion> {

    /**
     * Defines the insertion point relative to the specified position.
     */
    public enum InsertionPoint {
        /** Insert text before the character at the specified position */
        BEFORE,
        /** Insert text after the character at the specified position */
        AFTER
    }

    private final int line;
    private final int column;
    private final String text;
    private final InsertionPoint type;
    private final int order;

    /**
     * Creates a new insertion.
     *
     * @param line   1-based line number in source
     * @param column 1-based column number in source
     * @param text   the text to insert
     * @param type   whether to insert before or after the position
     * @param order  ordering for multiple insertions at the same position (lower = first)
     */
    public Insertion(int line, int column, String text, InsertionPoint type, int order) {
        this.line = line;
        this.column = column;
        this.text = Objects.requireNonNull(text, "text cannot be null");
        this.type = Objects.requireNonNull(type, "type cannot be null");
        this.order = order;
    }

    /**
     * Creates an insertion that inserts text before the specified position.
     *
     * @param line   1-based line number in source
     * @param column 1-based column number in source
     * @param text   the text to insert
     * @param order  ordering for multiple insertions at the same position (lower = first)
     * @return a new Insertion with type BEFORE
     */
    public static Insertion before(int line, int column, String text, int order) {
        return new Insertion(line, column, text, InsertionPoint.BEFORE, order);
    }

    /**
     * Creates an insertion that inserts text after the specified position.
     *
     * @param line   1-based line number in source
     * @param column 1-based column number in source
     * @param text   the text to insert
     * @param order  ordering for multiple insertions at the same position (lower = first)
     * @return a new Insertion with type AFTER
     */
    public static Insertion after(int line, int column, String text, int order) {
        return new Insertion(line, column, text, InsertionPoint.AFTER, order);
    }

    public int getLine() {
        return line;
    }

    public int getColumn() {
        return column;
    }

    public String getText() {
        return text;
    }

    public InsertionPoint getType() {
        return type;
    }

    public int getOrder() {
        return order;
    }

    /**
     * Compares insertions for sorting.
     * <p>
     * Sort order: line DESC, then column DESC, then order DESC.
     * This allows applying insertions from end to start to avoid position shifts.
     * Order is DESC because when two insertions target the same position, the one
     * with the higher order value is applied first (pushed right), so the one with
     * the lower order value ends up in front after being inserted at the same offset.
     * </p>
     *
     * @param other the insertion to compare to
     * @return comparison result
     */
    @Override
    public int compareTo(Insertion other) {
        // Line DESC (reverse order)
        int lineCompare = Integer.compare(other.line, this.line);
        if (lineCompare != 0) {
            return lineCompare;
        }

        // Column DESC (reverse order)
        int columnCompare = Integer.compare(other.column, this.column);
        if (columnCompare != 0) {
            return columnCompare;
        }

        // Order DESC (reverse order — higher order applied first at same position)
        return Integer.compare(other.order, this.order);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Insertion insertion = (Insertion) o;
        return line == insertion.line &&
                column == insertion.column &&
                order == insertion.order &&
                Objects.equals(text, insertion.text) &&
                type == insertion.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(line, column, text, type, order);
    }

    @Override
    public String toString() {
        return String.format("Insertion{line=%d, column=%d, type=%s, order=%d, text='%s'}",
                line, column, type, order, text.length() > 20 ? text.substring(0, 20) + "..." : text);
    }
}
