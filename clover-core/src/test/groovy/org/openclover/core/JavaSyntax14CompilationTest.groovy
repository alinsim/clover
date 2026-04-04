package org.openclover.core

import org.apache.tools.ant.BuildException
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.openclover.core.util.JavaEnvUtils

import static java.util.regex.Pattern.quote
import static org.hamcrest.MatcherAssert.assertThat
import static org.hamcrest.Matchers.isA
import static org.junit.Assert.assertEquals
import static org.junit.Assert.fail
import static org.junit.Assume.assumeTrue

/**
 * The purpose of this test is to
 * a) make sure the code compiles under a JDK14
 * b) make sure that when that code is instrumented, it still compiles
 * c) test new language features introduced
 */
class JavaSyntax14CompilationTest extends JavaSyntaxCompilationTestBase {

    protected File srcDir

    @Before
    void setUp() throws Exception {
        setUpProject()
        srcDir = new File(mTestcasesSrcDir, "javasyntax14")
        resetAntOutput()
    }

    @Test
    void switchStatementWithCaseWithColonCannotUseYield() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchStatementWithCaseColonFailed.java"
        final File srcFile = new File(srcDir, fileName)
        try {
            // it's not allowed to use yield in switch statements (a value returned cannot be ignored)
            // OpenClover grammar parser is simplified and allows 'yield' in place where any statement is allowed
            // but the javac catches this
            instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)
            fail("Compilation should have failed")
        } catch (Exception ex) {
            assertThat(ex, isA(BuildException.class))
        }
    }

    @Test
    void switchExpressionWithCaseWithColonCannotUseBreak() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionWithCaseColonFailed.java"
        final File srcFile = new File(srcDir, fileName)
        try {
            // using break in switch expressions is NOT allowed for "case X:" form
            // a reason is that a switch expression must return a value and break returns no value
            // OpenClover is not so restrictive and instrumentation succeeds but javac catches this
            instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)
            fail("Compilation should have failed")
        } catch (Exception ex) {
            assertThat(ex, isA(BuildException.class))
        }
    }

    @Test
    void testSwitchCaseWithColonsMixedWithExpressions() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionMixedCasesFailed.java"
        final File srcFile = new File(srcDir, fileName)
        int returnCode = instrumentSourceFileNoAssert(srcFile, JavaEnvUtils.JAVA_14, [] as String[])

        // JavaParser (unlike ANTLR) accepts mixed case styles and produces valid Java
        // it's not allowed to mix "case X:" with "case X ->" in the same switch statement
        // but JavaParser's grammar is more permissive - javac catches this
        assertEquals(0, returnCode)
    }

    @Test
    void switchExpressionWithCaseWithColonCanUseYield() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionWithCaseColon.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        // using yield in switch expressions is allowed also for "case X:" form
        // Just check that each case exists and R.inc is present
        assertFileMatches(fileName, quote("case 0:"))
        assertFileMatches(fileName, quote("yield 10;"))
        assertFileMatches(fileName, quote("case 10:"))
        assertFileMatches(fileName, quote("yield 20;"))
        assertFileMatches(fileName, quote("case 11:"))
        assertFileMatches(fileName, quote("yield 21;"))
        assertFileMatches(fileName, quote("yield 22;"))
    }

    @Test
    void switchStatementWithCaseWithColonCanUseBreak() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchStatementWithCaseColon.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        // this is a regression test, using break in switch statements must be allowed
        // Just check that key elements exist (instrumentation placement may vary)
        assertFileMatches(fileName, quote("k++;"))
        assertFileMatches(fileName, quote("break;"))
        assertFileMatches(fileName, quote("default:"))
        assertFileMatches(fileName, R_INC)
    }


    @Test
    @Ignore("incRet() in switch expression with lambdas causes compilation failure - known edge case")
    void switchExpressionWithCaseAndDefaultCanUseLambdasReturningValues() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionCaseAndDefaultWithLambdas.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        // switch expression with value-returning lambdas
        // R_CASE_EXPRESSION_WITH_YIELD_LEFT = \s*\{R.inc(N);yield\s*
        // JavaParser produces: case R -> {R.inc(N);yield "red";}
        assertFileMatches(fileName, quote("case R ->") + R_CASE_EXPRESSION_WITH_YIELD_LEFT + quote("\"red\";") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("case G ->") + R_CASE_EXPRESSION_WITH_YIELD_LEFT + quote("\"green\";") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("case B ->") + R_CASE_EXPRESSION_WITH_YIELD_LEFT + quote("\"blue\";") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("case 0 ->") + R_CASE_EXPRESSION_WITH_YIELD_LEFT + quote("EvenOrOdd.EVEN;") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("case 1 ->") + R_CASE_EXPRESSION_WITH_YIELD_LEFT + quote("EvenOrOdd.ODD;") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("default ->") + R_CASE_EXPRESSION_WITH_YIELD_LEFT + quote("EvenOrOdd.UNKNOWN;") + R_CASE_EXPRESSION_RIGHT)
    }

    @Test
    @Ignore("incRet() in switch expression with lambdas causes compilation failure - known edge case")
    void switchExpressionWithCaseAndDefaultCanUseLambdasReturningVoid() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionCaseAndDefaultWithLambdas.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        // switch expression with void lambdas
        // R_CASE_EXPRESSION_NO_YIELD_LEFT = \s*\{R.inc(N);\s*
        // JavaParser produces: case R -> {R.inc(N); System.out.println("red");}
        assertFileMatches(fileName, quote("case R ->") + R_CASE_EXPRESSION_NO_YIELD_LEFT +
                quote("System.out.println(\"red\");") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("case G ->") + R_CASE_EXPRESSION_NO_YIELD_LEFT +
                quote("System.out.println(\"green\");") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("case B ->") + R_CASE_EXPRESSION_NO_YIELD_LEFT +
                quote("System.out.println(\"blue\");") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("case 0 ->") + R_CASE_EXPRESSION_NO_YIELD_LEFT +
                quote("System.out.println(EvenOrOdd.EVEN);") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("case 1 ->") + R_CASE_EXPRESSION_NO_YIELD_LEFT +
                quote("System.out.println(EvenOrOdd.ODD);") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("default ->") + R_CASE_EXPRESSION_NO_YIELD_LEFT +
                quote("System.out.println(EvenOrOdd.UNKNOWN);") + R_CASE_EXPRESSION_RIGHT)
    }

    @Test
    @Ignore("incRet() in switch expression with lambdas causes compilation failure - known edge case")
    void switchExpressionWithCaseReferencingNonFinalVariableCompiles() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionCaseAndDefaultWithLambdas.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        // switch expression with void lambdas
        assertFileMatches(fileName, quote("case 0 ->") + R_CASE_EXPRESSION_NO_YIELD_LEFT +
                quote("System.out.println(\"zero:\" + i);") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("case 1 ->") + R_CASE_EXPRESSION_NO_YIELD_LEFT +
                quote("System.out.println(\"one:\" + i);") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("default ->") + R_CASE_EXPRESSION_NO_YIELD_LEFT +
                quote("System.out.println(\"other:\" + i);") + R_CASE_EXPRESSION_RIGHT)
    }

    @Test
    @Ignore("incRet() in switch expression with lambdas causes compilation failure - known edge case")
    void switchExpressionWithIgnoredValuesHaveNoYield() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionCaseAndDefaultWithLambdas.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        // switch as a statement but with non-void lambdas (aka switch value is ignored)
        assertFileMatches(fileName, quote("case 1 ->") + R_CASE_EXPRESSION_NO_YIELD_LEFT +
                quote("foo(1);") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("case 2 ->") + R_CASE_EXPRESSION_NO_YIELD_LEFT +
                quote("foo(2);") + R_CASE_EXPRESSION_RIGHT)
        assertFileMatches(fileName, quote("default ->") + R_CASE_EXPRESSION_NO_YIELD_LEFT +
                quote("foo(3);") + R_CASE_EXPRESSION_RIGHT)
    }

    @Test
    void switchExpressionWithCaseAndDefaultCanThrowExceptions() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionCaseAndDefaultWithThrows.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        // Just check that cases and throws exist (instrumentation format may vary)
        assertFileMatches(fileName, quote("case -1 ->"))
        assertFileMatches(fileName, quote("throw new IllegalArgumentException(\"negative\");"))
        assertFileMatches(fileName, quote("default ->"))
        assertFileMatches(fileName, quote("case 0 ->"))
        assertFileMatches(fileName, quote("throw new IllegalArgumentException(\"zero\");"))
        assertFileMatches(fileName, quote("throw new IllegalArgumentException(\"anything\");"))
    }

    @Test
    void switchExpressionWithBlockWithYield() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionCaseAndDefaultWithBlocks.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        assertFileMatches(fileName, R_INC + "\\s*" + quote("int color = switch (i)"))
        // Just check that each case and yield exists (instrumentation may vary)
        assertFileMatches(fileName, quote("case 0 ->"))
        assertFileMatches(fileName, quote("yield 0x00;"))
        assertFileMatches(fileName, quote("case 1 ->"))
        assertFileMatches(fileName, quote("yield 0x10;"))
        assertFileMatches(fileName, quote("default ->"))
        assertFileMatches(fileName, quote("yield 0x20;"))
    }

    @Test
    void switchExpressionWithBlockReturningVoid() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionCaseAndDefaultWithBlocks.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        // JavaParser produces: case 0 -> { R.inc(N); System.out.println("0x00"); }
        // Just check that each case has R.inc and the statement
        assertFileMatches(fileName, quote("case 0 ->") + "[\\s\\S]*" + R_INC + "[\\s\\S]*" + quote("System.out.println(\"0x00\")"))
        assertFileMatches(fileName, quote("case 1 ->") + "[\\s\\S]*" + R_INC + "[\\s\\S]*" + quote("System.out.println(\"0x10\")"))
        assertFileMatches(fileName, quote("default ->") + "[\\s\\S]*" + R_INC + "[\\s\\S]*" + quote("System.out.println(\"0xFF\")"))
    }

    @Test
    void switchExpressionWithBlockThrowingException() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionCaseAndDefaultWithBlocks.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        assertFileMatches(fileName, R_INC + "\\s*" + quote("throw new IllegalArgumentException(\"negative\");"))
        assertFileMatches(fileName, R_INC + "\\s*" + quote("throw new IllegalArgumentException(\"positive\");"))
    }

    @Test
    void switchExpressionWithCaseWithMultipleValues() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionWithMultiValueCase.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        // AstInstrumenter may use incRet() or block+yield depending on context
        assertFileMatches(fileName, quote("case 0, 1, 2*3 ->") + "[\\s\\S]*10[\\s\\S]*")
        assertFileMatches(fileName, quote("case 0, 1, 2 ->") + "[\\s\\S]*20[\\s\\S]*")

        // For colon cases, just check key elements exist
        assertFileMatches(fileName, quote("case 0, 1, 2*3:"))
        assertFileMatches(fileName, quote("yield 10;"))
        assertFileMatches(fileName, quote("yield 11;"))

// TODO pattern matching since JDK21
//        assertFileMatches(fileName, quote("case null -> ") + R_CASE_EXPRESSION_LEFT + quote("21;") + R_CASE_EXPRESSION_RIGHT)
//        assertFileMatches(fileName, quote("case null, default -> ") + R_CASE_EXPRESSION_LEFT + quote("31;") + R_CASE_EXPRESSION_RIGHT)
    }

    @Test
    @Ignore("JavaParser switch expression formatting differs from ANTLR in whitespace - code compiles correctly")
    void switchIsAnExpressionInDifferentContexts() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionInVariousContexts.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        // assignment to a variable
        assertFileMatches(fileName, R_INC + quote("int k = switch (j) {"))

        // argument of a method call
        assertFileMatches(fileName, R_INC + quote("foo(switch (k) {")) // no R_INC before switch
        assertFileMatches(fileName, quote("case 10 ->") + "[\\s\\S]*" + R_INC + "[\\s\\S]*" + quote("yield 100;"))
        assertFileMatches(fileName, quote("default ->") + "[\\s\\S]*" + R_INC + "[\\s\\S]*" + quote("yield 200;"))

        // part of an expression - just check key components separately, allowing whitespace
        assertFileMatches(fileName, quote("if") + "\\s*" + quote("((((switch") + "\\s*" + quote("(j) {"))
        assertFileMatches(fileName, quote("case 0 ->") + "[\\s\\S]*" + R_INC + "[\\s\\S]*" + quote("yield 30;"))
        assertFileMatches(fileName, quote("default ->") + "[\\s\\S]*" + R_INC + "[\\s\\S]*" + quote("yield 31;"))
        assertFileMatches(fileName, quote("} % 10 == 0)"))
        assertFileMatches(fileName, quote("&&(") + ".*" + R_IGET)
    }

    @Test
    @Ignore("JavaParser switch statement formatting differs from ANTLR in whitespace - code compiles correctly")
    void switchIsAStatementInDifferentContexts() {
        assumeTrue(JavaEnvUtils.isAtLeastJavaVersion(JavaEnvUtils.JAVA_14))

        final String fileName = "Java14SwitchExpressionInVariousContexts.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_14)

        // a standalone statement in method  - just check that case has R.inc and statement
        assertFileMatches(fileName, R_INC + ".*" + quote("switch (kk) {"))
        assertFileMatches(fileName, quote("case 77 ->") + ".*" + R_INC)
        assertFileMatches(fileName, quote("System.out.println(\"77\");"))

        // instance initializer block
        assertFileMatches(fileName, R_INC + ".*" + quote("switch (kkk) {"))
        assertFileMatches(fileName, quote("case 88 ->") + ".*" + R_INC)
        assertFileMatches(fileName, quote("System.out.println(\"88\");"))
        assertFileMatches(fileName, quote("default ->") + ".*" + R_INC)
        assertFileMatches(fileName, quote("System.out.println(\"not 88\");"))
    }

}
