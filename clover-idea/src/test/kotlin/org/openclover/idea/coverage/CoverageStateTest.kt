package org.openclover.idea.coverage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LineCoverageStatusTest {

    @Test
    fun ordinalOrderIsCorrect() {
        // PARTIAL < UNCOVERED < COVERED (lower ordinal = worse coverage)
        assert(LineCoverageStatus.PARTIAL.ordinal < LineCoverageStatus.UNCOVERED.ordinal)
        assert(LineCoverageStatus.UNCOVERED.ordinal < LineCoverageStatus.COVERED.ordinal)
    }
}

class FileCoverageInfoTest {

    @Test
    fun emptyFileCoverageHasNoLines() {
        val info = FileCoverageInfo(
            filePath = "/test/Foo.java",
            lineStatuses = emptyMap(),
            numStatements = 0,
            numCoveredStatements = 0,
            numBranches = 0,
            numCoveredBranches = 0,
            percentCovered = 0f,
        )
        assertEquals(0, info.lineStatuses.size)
        assertEquals(0f, info.percentCovered)
    }

    @Test
    fun fileCoverageWithMixedLines() {
        val info = FileCoverageInfo(
            filePath = "/test/Foo.java",
            lineStatuses = mapOf(
                1 to LineCoverageStatus.COVERED,
                2 to LineCoverageStatus.UNCOVERED,
                3 to LineCoverageStatus.PARTIAL,
            ),
            numStatements = 3,
            numCoveredStatements = 1,
            numBranches = 1,
            numCoveredBranches = 0,
            percentCovered = 0.33f,
        )
        assertEquals(LineCoverageStatus.COVERED, info.lineStatuses[1])
        assertEquals(LineCoverageStatus.UNCOVERED, info.lineStatuses[2])
        assertEquals(LineCoverageStatus.PARTIAL, info.lineStatuses[3])
        assertNull(info.lineStatuses[4])
    }
}

class CoverageStateTest {

    @Test
    fun emptyStateIsDefault() {
        val state: CoverageState = CoverageState.Empty
        assert(state is CoverageState.Empty)
    }

    @Test
    fun errorStateCarriesMessage() {
        val state = CoverageState.Error("File not found")
        assertEquals("File not found", state.message)
    }
}
