package org.openclover.idea.editor

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.openclover.idea.coverage.FileCoverageInfo
import org.openclover.idea.coverage.LineCoverageStatus

class CoverageGutterRendererTest {

    private val fileCoverage = FileCoverageInfo(
        filePath = "/test/Foo.java",
        lineStatuses = mapOf(1 to LineCoverageStatus.COVERED),
            lineDetails = emptyMap(),
        numStatements = 10,
        numCoveredStatements = 5,
        numBranches = 2,
        numCoveredBranches = 1,
        percentCovered = 0.5f,
    )

    @Test
    fun coveredRendererHasCorrectIcon() {
        val renderer = CoverageGutterRenderer(LineCoverageStatus.COVERED, fileCoverage, 1)
        assertNotNull(renderer.icon)
    }

    @Test
    fun tooltipContainsLineNumber() {
        val renderer = CoverageGutterRenderer(LineCoverageStatus.UNCOVERED, fileCoverage, 42)
        assert(renderer.tooltipText!!.contains("42"))
    }

    @Test
    fun noClickAction() {
        val renderer = CoverageGutterRenderer(LineCoverageStatus.COVERED, fileCoverage, 1)
        assertNull(renderer.clickAction)
    }

    @Test
    fun equalsAndHashCode() {
        val a = CoverageGutterRenderer(LineCoverageStatus.COVERED, fileCoverage, 1)
        val b = CoverageGutterRenderer(LineCoverageStatus.COVERED, fileCoverage, 1)
        val c = CoverageGutterRenderer(LineCoverageStatus.UNCOVERED, fileCoverage, 1)

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
        assertNotEquals(a, c)
    }
}
