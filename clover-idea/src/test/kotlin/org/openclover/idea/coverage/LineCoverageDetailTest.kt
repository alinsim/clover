package org.openclover.idea.coverage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for LineCoverageDetail — per-line coverage info for tooltips.
 */
class LineCoverageDetailTest {

    @Test
    fun coveredStatementShowsHitCount() {
        val detail = LineCoverageDetail(
            status = LineCoverageStatus.COVERED,
            statementHits = 5,
        )
        assertTrue(detail.toTooltip(10).contains("5"), "Tooltip should contain hit count")
    }

    @Test
    fun uncoveredStatementShowsZeroHits() {
        val detail = LineCoverageDetail(
            status = LineCoverageStatus.UNCOVERED,
            statementHits = 0,
        )
        assertTrue(detail.toTooltip(10).contains("not covered"), "Tooltip should say not covered")
    }

    @Test
    fun partialBranchShowsTrueAndFalseHits() {
        val detail = LineCoverageDetail(
            status = LineCoverageStatus.PARTIAL,
            statementHits = 3,
            branchTrueHits = 3,
            branchFalseHits = 0,
        )
        val tooltip = detail.toTooltip(10)
        assertTrue(tooltip.contains("true"), "Should mention true branch")
        assertTrue(tooltip.contains("false"), "Should mention false branch")
        assertTrue(tooltip.contains("3"), "Should show true hit count")
        assertTrue(tooltip.contains("0"), "Should show false hit count")
    }

    @Test
    fun fullyBranchedShowsBothHits() {
        val detail = LineCoverageDetail(
            status = LineCoverageStatus.COVERED,
            statementHits = 10,
            branchTrueHits = 7,
            branchFalseHits = 3,
        )
        val tooltip = detail.toTooltip(42)
        assertTrue(tooltip.contains("7"), "Should show true branch hits")
        assertTrue(tooltip.contains("3"), "Should show false branch hits")
    }

    @Test
    fun methodEntryShowsMethodHits() {
        val detail = LineCoverageDetail(
            status = LineCoverageStatus.COVERED,
            statementHits = 0,
            methodHits = 12,
        )
        val tooltip = detail.toTooltip(5)
        assertTrue(tooltip.contains("12"), "Should show method entry count")
        assertTrue(tooltip.contains("method"), "Should mention method")
    }

    @Test
    fun tooltipIncludesLineNumber() {
        val detail = LineCoverageDetail(status = LineCoverageStatus.COVERED, statementHits = 1)
        val tooltip = detail.toTooltip(42)
        assertTrue(tooltip.contains("42"), "Tooltip should include line number")
    }
}
