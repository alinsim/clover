package org.openclover.core

import org.junit.Before
import org.junit.Test
import org.openclover.core.util.JavaEnvUtils

import static org.junit.Assert.assertFalse
import static org.junit.Assert.assertTrue

/**
 * Compilation tests for globalSliceStart/End test method wrapper.
 * Verifies that instrumented test methods compile and run correctly
 * across various Java patterns.
 */
class JavaSyntaxTestMethodCompilationTest extends JavaSyntaxCompilationTestBase {

    private File srcDir

    @Before
    void setUp() throws Exception {
        setUpProject()
        srcDir = new File(mTestcasesSrcDir, "javasyntax1.8")
        resetAntOutput()
    }

    @Test
    void testTestMethodWrapperCompiles() {
        final String fileName = "TestMethodInstrumentation.java"
        // Just verify it compiles - don't try to run (requires ANTLR at runtime)
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_8)
    }

    @Test
    void testInstrumentedTestMethodContainsGlobalSlice() {
        final String fileName = "TestMethodInstrumentation.java"
        instrumentAndCompileSourceFile(srcDir, mGenSrcDir, fileName, JavaEnvUtils.JAVA_8)

        assertFileMatches(fileName, ".*globalSliceStart.*", false)
        assertFileMatches(fileName, ".*globalSliceEnd.*", false)
        assertFileMatches(fileName, ".*try\\s*\\{.*", false)
        assertFileMatches(fileName, ".*catch.*Throwable.*", false)
    }

    @Test
    void testNonTestMethodHasNoGlobalSlice() {
        final String fileName = "TestMethodInstrumentation.java"
        instrumentSourceFile(new File(srcDir, fileName), JavaEnvUtils.JAVA_8)

        File instrumented = new File(mGenSrcDir, fileName)
        String content = instrumented.text

        // notATest method should have R.inc() but NOT globalSliceStart
        // Split on "notATest" — the part after it should not contain globalSliceStart
        int notATestPos = content.indexOf("void notATest()")
        assertTrue(notATestPos > 0)
        String afterNotATest = content.substring(notATestPos, Math.min(notATestPos + 200, content.length()))
        assertFalse(afterNotATest.contains("globalSliceStart"))
    }
}
