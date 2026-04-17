package org.openclover.idea.editor

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openclover.idea.coverage.FileCoverageInfo
import org.openclover.idea.coverage.LineCoverageStatus

/**
 * Tests for CoverageEditorAnnotator behavior.
 * These test the annotation logic itself, not the IntelliJ integration.
 */
class CoverageTextAttributesTest {

    @Test
    fun textAttributesAreDifferentForEachStatus() {
        val covered = CoverageEditorAnnotator.textAttributesFor(LineCoverageStatus.COVERED)
        val uncovered = CoverageEditorAnnotator.textAttributesFor(LineCoverageStatus.UNCOVERED)
        val partial = CoverageEditorAnnotator.textAttributesFor(LineCoverageStatus.PARTIAL)

        assertNotNull(covered.backgroundColor)
        assertNotNull(uncovered.backgroundColor)
        assertNotNull(partial.backgroundColor)

        // All three must have distinct colors
        assertTrue(covered.backgroundColor != uncovered.backgroundColor,
            "Covered and uncovered must have different colors")
        assertTrue(covered.backgroundColor != partial.backgroundColor,
            "Covered and partial must have different colors")
        assertTrue(uncovered.backgroundColor != partial.backgroundColor,
            "Uncovered and partial must have different colors")
    }

    @Test
    fun fileCoverageInfoMatchesByAbsolutePath() {
        // Verify that the lookup key is the absolute path
        val coverage = FileCoverageInfo(
            filePath = "/project/src/main/java/Foo.java",
            lineStatuses = mapOf(1 to LineCoverageStatus.COVERED),
            numStatements = 1,
            numCoveredStatements = 1,
            numBranches = 0,
            numCoveredBranches = 0,
            percentCovered = 1.0f,
        )

        val coverageMap = mapOf(coverage.filePath to coverage)

        // Lookup by exact path should work
        assertNotNull(coverageMap["/project/src/main/java/Foo.java"])
        // Lookup by different path should not
        assertTrue(coverageMap["/other/path/Foo.java"] == null)
    }
}
