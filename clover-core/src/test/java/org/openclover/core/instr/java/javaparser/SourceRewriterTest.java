package org.openclover.core.instr.java.javaparser;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests for {@link SourceRewriter} and {@link Insertion}.
 */
public class SourceRewriterTest {

    private static final String CLASS_FOO_EMPTY = "class Foo {}";
    private static final String CLASS_FOO_SPACED = "class Foo { }";
    private static final String MARK_COMMENT = "/*mark*/";
    private static final String MARKER_A = "A;";
    private static final String MARKER_B = "B;";
    private static final String INC_CALL = "R.inc(0);";

    @Test
    public void rewriteWithEmptyInsertionsReturnsOriginal() {
        assertEquals(CLASS_FOO_EMPTY, SourceRewriter.rewrite(CLASS_FOO_EMPTY, Collections.<Insertion>emptyList()));
    }

    @Test
    public void rewriteWithNullInsertionsReturnsOriginal() {
        assertEquals(CLASS_FOO_EMPTY, SourceRewriter.rewrite(CLASS_FOO_EMPTY, null));
    }

    @Test
    public void rewriteInsertBeforeSinglePosition() {
        String source = "class Foo { void bar() {} }";
        List<Insertion> insertions = Collections.singletonList(
                Insertion.before(1, 25, MARK_COMMENT, 0)
        );
        String result = SourceRewriter.rewrite(source, insertions);
        assertTrue(result.contains(MARK_COMMENT));
    }

    @Test
    public void rewriteInsertAfterOpenBrace() {
        // Column: 1234567890123
        List<Insertion> insertions = Collections.singletonList(
                Insertion.after(1, 11, " int x;", 0)
        );
        String result = SourceRewriter.rewrite(CLASS_FOO_SPACED, insertions);
        assertEquals("class Foo { int x; }", result);
    }

    @Test
    public void rewriteMultipleInsertionsAtDifferentPositions() {
        String source = "class Foo {\n  void bar() {\n  }\n}";
        List<Insertion> insertions = Arrays.asList(
                Insertion.after(1, 11, " /*class*/", 0),
                Insertion.after(2, 17, " /*method*/", 0)
        );
        String result = SourceRewriter.rewrite(source, insertions);
        assertTrue(result.contains("/*class*/"));
        assertTrue(result.contains("/*method*/"));
    }

    @Test
    public void rewriteMultipleInsertionsAtSamePositionRespectOrder() {
        List<Insertion> insertions = Arrays.asList(
                Insertion.after(1, 11, MARKER_A, 0),
                Insertion.after(1, 11, MARKER_B, 1)
        );
        String result = SourceRewriter.rewrite(CLASS_FOO_SPACED, insertions);
        assertTrue("A should appear before B", result.indexOf(MARKER_A) < result.indexOf(MARKER_B));
    }

    @Test
    public void rewriteHandlesDosLineEndings() {
        String source = "class Foo {\r\n  void bar() {\r\n  }\r\n}";
        List<Insertion> insertions = Collections.singletonList(
                Insertion.after(2, 17, INC_CALL, 0)
        );
        String result = SourceRewriter.rewrite(source, insertions);
        assertTrue(result.contains(INC_CALL));
    }

    @Test
    public void addMarkerPrependsCloverMarker() {
        String result = SourceRewriter.addMarker(CLASS_FOO_EMPTY);
        assertTrue(result.startsWith("/* $$ This file has been instrumented by OpenClover"));
        assertTrue(result.endsWith(CLASS_FOO_EMPTY));
    }

    @Test
    public void insertionComparableOrdersCorrectly() {
        Insertion a = Insertion.after(3, 5, "A", 0);
        Insertion b = Insertion.after(1, 5, "B", 0);
        Insertion c = Insertion.after(3, 10, "C", 0);

        List<Insertion> list = new ArrayList<>(Arrays.asList(b, a, c));
        Collections.sort(list);

        // Should be sorted: line DESC, column DESC
        // c (3,10), a (3,5), b (1,5)
        assertEquals("C", list.get(0).getText());
        assertEquals("A", list.get(1).getText());
        assertEquals("B", list.get(2).getText());
    }
}
