package org.openclover.core.instr.java.javaparser;

import org.junit.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * TDD tests for branch instrumentation correctness.
 *
 * Verifies that both true AND false branches get R.inc() calls for:
 * - if-then (no else): synthetic else { R.inc(falseIndex); } must be added
 * - if-then-else: true gets R.inc(N), else gets R.inc(N+1)
 * - while/for/do-while: only loop body (true branch) instrumented
 */
public class BranchInstrumentationTest {

    private static final String IF_WITHOUT_ELSE =
            "class Foo { void m(boolean x) { if (x) { doSomething(); } doOther(); } }";
    private static final String IF_WITH_ELSE =
            "class Foo { void m(boolean x) { if (x) { doA(); } else { doB(); } } }";
    private static final String CLASS_FOO_INT = "class Foo { void m(int x) {";
    private static final String PROCESS = "process()";
    private static final Pattern SYNTHETIC_ELSE_PATTERN =
            Pattern.compile("else\\s*\\{\\s*__CLR_TEST\\.inc\\((\\d+)\\)\\s*;\\s*\\}");
    private static final Pattern THEN_INC_PATTERN =
            Pattern.compile("if\\s*\\(x\\)\\s*\\{\\s*__CLR_TEST\\.inc\\((\\d+)\\)");
    private static final Pattern ELSE_INC_PATTERN =
            Pattern.compile("else\\s*\\{\\s*__CLR_TEST\\.inc\\((\\d+)\\)");
    private static final String THEN_MUST_HAVE_INC = "then block must have R.inc";

    @Test
    public void ifWithoutElseInstrumentsFalseBranch() throws Exception {
        String instrumented = TestInstrumentationHelper.instrument(IF_WITHOUT_ELSE);

        Matcher matcher = SYNTHETIC_ELSE_PATTERN.matcher(instrumented);
        assertTrue("if-without-else must have synthetic else block with R.inc", matcher.find());
    }

    @Test
    public void ifWithoutElseFalseBranchIndexIsTruePlusOne() throws Exception {
        String instrumented = TestInstrumentationHelper.instrument(IF_WITHOUT_ELSE);

        Matcher thenMatcher = THEN_INC_PATTERN.matcher(instrumented);
        assertTrue(THEN_MUST_HAVE_INC, thenMatcher.find());
        int trueIndex = Integer.parseInt(thenMatcher.group(1));

        Matcher falseMatcher = SYNTHETIC_ELSE_PATTERN.matcher(instrumented);
        assertTrue("synthetic else must have R.inc", falseMatcher.find());
        int falseIndex = Integer.parseInt(falseMatcher.group(1));

        assertEquals("false index must be true index + 1", trueIndex + 1, falseIndex);
    }

    @Test
    public void ifElseInstrumentsBothBranches() throws Exception {
        String instrumented = TestInstrumentationHelper.instrument(IF_WITH_ELSE);

        Matcher thenMatcher = THEN_INC_PATTERN.matcher(instrumented);
        Matcher elseMatcher = ELSE_INC_PATTERN.matcher(instrumented);

        assertTrue(THEN_MUST_HAVE_INC, thenMatcher.find());
        assertTrue("else block must have R.inc", elseMatcher.find());
    }

    @Test
    public void ifElseUsesConsecutiveIndices() throws Exception {
        String instrumented = TestInstrumentationHelper.instrument(IF_WITH_ELSE);

        Matcher thenMatcher = THEN_INC_PATTERN.matcher(instrumented);
        Matcher elseMatcher = ELSE_INC_PATTERN.matcher(instrumented);

        assertTrue(THEN_MUST_HAVE_INC, thenMatcher.find());
        assertTrue("else must have R.inc", elseMatcher.find());

        int trueIndex = Integer.parseInt(thenMatcher.group(1));
        int falseIndex = Integer.parseInt(elseMatcher.group(1));

        assertEquals("else index must be then index + 1", trueIndex + 1, falseIndex);
    }

    @Test
    public void nestedIfElseEachLevelGetsBranchPair() throws Exception {
        String source = CLASS_FOO_INT
                + " if (x > 0) { if (x > 10) { big(); } else { small(); } }"
                + " else { negative(); } } }";
        String instrumented = TestInstrumentationHelper.instrument(source);

        assertTrue("big() branch instrumented", TestInstrumentationHelper.hasBranchIncBefore(instrumented, "big()"));
        assertTrue("small() branch instrumented", TestInstrumentationHelper.hasBranchIncBefore(instrumented, "small()"));
        assertTrue("negative() branch instrumented", TestInstrumentationHelper.hasBranchIncBefore(instrumented, "negative()"));
    }

    @Test
    public void whileLoopOnlyInstrumentsBody() throws Exception {
        String source = "class Foo { void m() { while (hasNext()) { process(); } } }";
        String instrumented = TestInstrumentationHelper.instrument(source);

        assertTrue("while body should be instrumented", TestInstrumentationHelper.hasBranchIncBefore(instrumented, PROCESS));
        // while loops should NOT get synthetic else blocks
        assertFalse("while should not get synthetic else", SYNTHETIC_ELSE_PATTERN.matcher(instrumented).find());
    }

    @Test
    public void forLoopOnlyInstrumentsBody() throws Exception {
        String source = "class Foo { void m() { for (int i = 0; i < 10; i++) { process(); } } }";
        String instrumented = TestInstrumentationHelper.instrument(source);

        assertTrue("for body should be instrumented", TestInstrumentationHelper.hasBranchIncBefore(instrumented, PROCESS));
    }

    @Test
    public void elseIfChainEachConditionGetsBranchPair() throws Exception {
        String source = CLASS_FOO_INT
                + " if (x == 1) { one(); } else if (x == 2) { two(); } else { other(); } } }";
        String instrumented = TestInstrumentationHelper.instrument(source);

        assertTrue("one() branch instrumented", TestInstrumentationHelper.hasBranchIncBefore(instrumented, "one()"));
        assertTrue("two() branch instrumented", TestInstrumentationHelper.hasBranchIncBefore(instrumented, "two()"));
        assertTrue("other() branch instrumented", TestInstrumentationHelper.hasBranchIncBefore(instrumented, "other()"));
    }
}
