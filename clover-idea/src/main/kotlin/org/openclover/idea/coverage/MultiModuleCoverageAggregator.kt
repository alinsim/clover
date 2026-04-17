package org.openclover.idea.coverage

import java.io.File

/**
 * Aggregates coverage data from multiple modules in a multi-module project.
 *
 * Scans the project directory tree for .clover/clover.db files in each module
 * and merges the coverage data into a unified view.
 */
class MultiModuleCoverageAggregator {

    /**
     * Scan a directory tree for Clover database files.
     *
     * Looks for .clover/clover.db files in any subdirectory.
     *
     * @param rootDir Root directory to scan
     * @return List of database file paths found
     */
    fun scanForDatabases(rootDir: File): List<File> {
        if (!rootDir.exists() || !rootDir.isDirectory) {
            return emptyList()
        }

        val databases = mutableListOf<File>()
        scanRecursive(rootDir, databases)
        return databases
    }

    private fun scanRecursive(dir: File, result: MutableList<File>, depth: Int = 0) {
        // Limit recursion depth to avoid deep traversals
        if (depth > 10) return

        // Skip hidden directories except .clover
        if (dir.name.startsWith(".") && dir.name != ".clover") return

        // Skip common non-module directories
        if (dir.name in setOf("target", "build", "out", "node_modules", ".git", ".idea")) return

        // Check if this directory contains clover.db
        val dbFile = File(dir, "clover.db")
        if (dbFile.exists() && dbFile.isFile) {
            result.add(dbFile)
            return // Don't recurse deeper if we found a database
        }

        // Recurse into subdirectories
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (child.isDirectory) {
                scanRecursive(child, result, depth + 1)
            }
        }
    }

    /**
     * Merge multiple file coverage maps into a single unified map.
     *
     * If the same file path appears in multiple maps, the coverage data
     * is combined (preferring the map with higher coverage).
     *
     * @param maps List of file coverage maps to merge
     * @return Merged coverage map
     */
    fun mergeFileCoverageMaps(maps: List<Map<String, FileCoverageInfo>>): Map<String, FileCoverageInfo> {
        if (maps.isEmpty()) {
            return emptyMap()
        }

        if (maps.size == 1) {
            return maps.first()
        }

        val merged = mutableMapOf<String, FileCoverageInfo>()

        for (map in maps) {
            for ((filePath, coverage) in map) {
                val existing = merged[filePath]
                if (existing == null) {
                    merged[filePath] = coverage
                } else {
                    // Merge coverage: prefer higher coverage data
                    merged[filePath] = mergeCoverageInfo(existing, coverage)
                }
            }
        }

        return merged
    }

    /**
     * Merge two FileCoverageInfo objects for the same file.
     *
     * Takes the union of line statuses, preferring COVERED over UNCOVERED.
     */
    private fun mergeCoverageInfo(a: FileCoverageInfo, b: FileCoverageInfo): FileCoverageInfo {
        // Merge line statuses: take the "better" status for each line
        val mergedStatuses = (a.lineStatuses.keys + b.lineStatuses.keys).associateWith { line ->
            val statusA = a.lineStatuses[line]
            val statusB = b.lineStatuses[line]
            when {
                statusA == LineCoverageStatus.COVERED || statusB == LineCoverageStatus.COVERED ->
                    LineCoverageStatus.COVERED
                statusA == LineCoverageStatus.PARTIAL || statusB == LineCoverageStatus.PARTIAL ->
                    LineCoverageStatus.PARTIAL
                else -> LineCoverageStatus.UNCOVERED
            }
        }

        // Merge line details: combine hit counts
        val mergedDetails = (a.lineDetails.keys + b.lineDetails.keys).associateWith { line ->
            val detailA = a.lineDetails[line]
            val detailB = b.lineDetails[line]
            when {
                detailA != null && detailB != null -> LineCoverageDetail(
                    status = mergedStatuses[line] ?: LineCoverageStatus.UNCOVERED,
                    statementHits = maxOf(detailA.statementHits, detailB.statementHits),
                    branchTrueHits = maxOf(detailA.branchTrueHits, detailB.branchTrueHits),
                    branchFalseHits = maxOf(detailA.branchFalseHits, detailB.branchFalseHits),
                    methodHits = maxOf(detailA.methodHits, detailB.methodHits)
                )
                detailA != null -> detailA
                detailB != null -> detailB
                else -> LineCoverageDetail(status = LineCoverageStatus.UNCOVERED)
            }
        }

        // Aggregate metrics
        val totalStatements = maxOf(a.numStatements, b.numStatements)
        val totalCovered = maxOf(a.numCoveredStatements, b.numCoveredStatements)
        val totalBranches = maxOf(a.numBranches, b.numBranches)
        val totalCoveredBranches = maxOf(a.numCoveredBranches, b.numCoveredBranches)

        return FileCoverageInfo(
            filePath = a.filePath,
            lineStatuses = mergedStatuses,
            lineDetails = mergedDetails,
            numStatements = totalStatements,
            numCoveredStatements = totalCovered,
            numBranches = totalBranches,
            numCoveredBranches = totalCoveredBranches,
            percentCovered = if (totalStatements > 0) totalCovered.toFloat() / totalStatements else 0f
        )
    }
}
