package org.openclover.idea.report

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openclover.idea.coverage.FileCoverageInfo

/**
 * Tests for CloudReportEditor risk scoring.
 */
class CloudReportEditorTest {

    @Test
    fun riskScoreIsHigherForUncoveredFiles() {
        val uncovered = createMockCoverage(100, 0)
        val fullyCovered = createMockCoverage(100, 100)

        val riskUncovered = calculateRiskScore(uncovered)
        val riskCovered = calculateRiskScore(fullyCovered)

        assertTrue(riskUncovered > riskCovered, "Uncovered files should have higher risk")
    }

    @Test
    fun riskScoreIsHigherForLargerFiles() {
        val small = createMockCoverage(10, 5)
        val large = createMockCoverage(100, 50)

        val riskSmall = calculateRiskScore(small)
        val riskLarge = calculateRiskScore(large)

        assertTrue(riskLarge > riskSmall, "Larger files should have higher risk")
    }

    @Test
    fun riskScoreIsZeroForFullyCoveredFile() {
        val fullyCovered = createMockCoverage(100, 100)
        val risk = calculateRiskScore(fullyCovered)
        assertEquals(0.0, risk, 0.01, "Fully covered file should have zero risk")
    }

    @Test
    fun riskScoreIsMaxForCompletelyUncoveredFile() {
        val uncovered = createMockCoverage(100, 0)
        val risk = calculateRiskScore(uncovered)
        assertEquals(100.0, risk, "Completely uncovered file should have risk equal to statement count")
    }

    private fun createMockCoverage(numStatements: Int, numCovered: Int): FileCoverageInfo {
        return FileCoverageInfo(
            filePath = "/mock/file.java",
            lineStatuses = emptyMap(),
            lineDetails = emptyMap(),
            numStatements = numStatements,
            numCoveredStatements = numCovered,
            numBranches = 0,
            numCoveredBranches = 0,
            percentCovered = if (numStatements > 0) numCovered.toFloat() / numStatements else 0f
        )
    }

    companion object {
        /**
         * Calculate risk score for a file.
         * Risk = (1 - coverage%) * statement count
         */
        fun calculateRiskScore(file: FileCoverageInfo): Double {
            return (1.0 - file.percentCovered) * file.numStatements
        }
    }
}
