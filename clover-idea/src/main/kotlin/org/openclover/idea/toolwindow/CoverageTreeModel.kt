package org.openclover.idea.toolwindow

import org.openclover.idea.coverage.FileCoverageInfo

/**
 * Hierarchical model for coverage data: root → packages → files.
 * Package nodes aggregate coverage metrics from their children.
 *
 * This is a pure data model — no IntelliJ dependencies — making it testable.
 */
class CoverageTreeModel private constructor(val root: TreeNode) {

    companion object {
        private const val DEFAULT_PACKAGE = "(default package)"

        /**
         * Build a tree from a flat list of file coverage entries.
         * Groups files by package (derived from the file path).
         */
        fun build(files: List<FileCoverageInfo>): CoverageTreeModel {
            val packageMap = mutableMapOf<String, MutableList<FileCoverageInfo>>()

            for (file in files) {
                val packageName = extractPackage(file.filePath)
                packageMap.getOrPut(packageName) { mutableListOf() }.add(file)
            }

            val root = TreeNode(name = "Project", isPackage = true)

            for ((pkgName, pkgFiles) in packageMap.toSortedMap()) {
                val packageNode = TreeNode(
                    name = pkgName,
                    isPackage = true,
                    totalStatements = pkgFiles.sumOf { it.numStatements },
                    coveredStatements = pkgFiles.sumOf { it.numCoveredStatements },
                    totalBranches = pkgFiles.sumOf { it.numBranches },
                    coveredBranches = pkgFiles.sumOf { it.numCoveredBranches },
                )

                for (file in pkgFiles.sortedBy { extractFileName(it.filePath) }) {
                    packageNode.children.add(
                        TreeNode(
                            name = extractFileName(file.filePath),
                            isPackage = false,
                            filePath = file.filePath,
                            totalStatements = file.numStatements,
                            coveredStatements = file.numCoveredStatements,
                            totalBranches = file.numBranches,
                            coveredBranches = file.numCoveredBranches,
                            percentCovered = file.percentCovered,
                        ),
                    )
                }

                root.children.add(packageNode)
            }

            return CoverageTreeModel(root)
        }

        private fun extractPackage(filePath: String): String {
            val normalized = filePath.replace('\\', '/')
            val lastSlash = normalized.lastIndexOf('/')
            if (lastSlash < 0) return DEFAULT_PACKAGE
            return normalized.substring(0, lastSlash).replace('/', '.')
        }

        private fun extractFileName(filePath: String): String {
            val normalized = filePath.replace('\\', '/')
            val lastSlash = normalized.lastIndexOf('/')
            return if (lastSlash >= 0) normalized.substring(lastSlash + 1) else normalized
        }
    }

    /**
     * A node in the coverage tree — either a package or a file.
     */
    data class TreeNode(
        val name: String,
        val isPackage: Boolean,
        val filePath: String? = null,
        val totalStatements: Int = 0,
        val coveredStatements: Int = 0,
        val totalBranches: Int = 0,
        val coveredBranches: Int = 0,
        val percentCovered: Float = 0f,
        val children: MutableList<TreeNode> = mutableListOf(),
    ) {
        val coveragePercent: Float
            get() = if (totalStatements > 0) coveredStatements.toFloat() / totalStatements else 0f
    }
}
