package org.openclover.idea.coverage

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

/**
 * Tests for MultiModuleCoverageAggregator.
 */
class MultiModuleCoverageAggregatorTest {

    @Test
    fun aggregatorCanBeInstantiated() {
        val aggregator = MultiModuleCoverageAggregator()
        assertNotNull(aggregator)
    }

    @Test
    fun scanForDatabasesFindsNothingInEmptyDir() {
        val tempDir = createTempDir()
        try {
            val aggregator = MultiModuleCoverageAggregator()
            val databases = aggregator.scanForDatabases(tempDir)
            assertTrue(databases.isEmpty(), "Should find no databases in empty directory")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun scanForDatabasesFindsCloverDb() {
        val tempDir = createTempDir()
        try {
            // Create a mock .clover/clover.db structure
            val cloverDir = File(tempDir, ".clover")
            cloverDir.mkdirs()
            val dbFile = File(cloverDir, "clover.db")
            dbFile.writeText("mock database")

            val aggregator = MultiModuleCoverageAggregator()
            val databases = aggregator.scanForDatabases(tempDir)

            assertEquals(1, databases.size, "Should find one database")
            assertTrue(databases.first().absolutePath.contains("clover.db"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun scanForDatabasesFindsMultipleModules() {
        val tempDir = createTempDir()
        try {
            // Module 1
            val module1 = File(tempDir, "module1/.clover")
            module1.mkdirs()
            File(module1, "clover.db").writeText("module1 db")

            // Module 2
            val module2 = File(tempDir, "module2/.clover")
            module2.mkdirs()
            File(module2, "clover.db").writeText("module2 db")

            val aggregator = MultiModuleCoverageAggregator()
            val databases = aggregator.scanForDatabases(tempDir)

            assertEquals(2, databases.size, "Should find two databases")
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun mergeFileCoverageMapsHandlesEmptyInput() {
        val aggregator = MultiModuleCoverageAggregator()
        val merged = aggregator.mergeFileCoverageMaps(emptyList())
        assertTrue(merged.isEmpty(), "Should return empty map for empty input")
    }

    @Test
    fun mergeFileCoverageMapsPreservesUniquePaths() {
        val map1 = mapOf("/path/to/FileA.java" to createMockCoverage(10, 5))
        val map2 = mapOf("/path/to/FileB.java" to createMockCoverage(20, 10))

        val aggregator = MultiModuleCoverageAggregator()
        val merged = aggregator.mergeFileCoverageMaps(listOf(map1, map2))

        assertEquals(2, merged.size, "Should have two files")
        assertTrue(merged.containsKey("/path/to/FileA.java"))
        assertTrue(merged.containsKey("/path/to/FileB.java"))
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
            percentCovered = numCovered.toFloat() / numStatements
        )
    }
}
