package org.openclover.idea.toolwindow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.openclover.idea.coverage.FileCoverageInfo
import org.openclover.idea.coverage.LineCoverageStatus

/**
 * Tests for CoverageTreeModel — hierarchical package→file coverage data.
 */
class CoverageTreeModelTest {

    @Test
    fun emptyModelHasNoChildren() {
        val model = CoverageTreeModel.build(emptyList())
        assertEquals(0, model.root.children.size, "Empty model should have no children")
    }

    @Test
    fun singleFileCreatesPackageNode() {
        val files = listOf(makeFile("com/example/Foo.java", 10, 8))
        val model = CoverageTreeModel.build(files)

        assertEquals(1, model.root.children.size)
        val pkgNode = model.root.children[0]
        assertEquals("com.example", pkgNode.name)
        assertEquals(1, pkgNode.children.size)
        assertEquals("Foo.java", pkgNode.children[0].name)
    }

    @Test
    fun multipleFilesInSamePackageGrouped() {
        val files = listOf(
            makeFile("com/example/Foo.java", 10, 8),
            makeFile("com/example/Bar.java", 5, 3),
        )
        val model = CoverageTreeModel.build(files)

        assertEquals(1, model.root.children.size, "Same package should be grouped")
        val pkgNode = model.root.children[0]
        assertEquals(2, pkgNode.children.size)
    }

    @Test
    fun differentPackagesCreateSeparateNodes() {
        val files = listOf(
            makeFile("com/example/Foo.java", 10, 8),
            makeFile("org/other/Bar.java", 5, 3),
        )
        val model = CoverageTreeModel.build(files)

        assertEquals(2, model.root.children.size, "Different packages should be separate")
    }

    @Test
    fun packageNodeAggregatesMetrics() {
        val files = listOf(
            makeFile("com/example/Foo.java", 10, 8),
            makeFile("com/example/Bar.java", 5, 3),
        )
        val model = CoverageTreeModel.build(files)

        val pkgNode = model.root.children[0]
        assertEquals(15, pkgNode.totalStatements)
        assertEquals(11, pkgNode.coveredStatements)
    }

    @Test
    fun defaultPackageHandled() {
        val files = listOf(makeFile("Foo.java", 5, 5))
        val model = CoverageTreeModel.build(files)

        assertEquals(1, model.root.children.size)
        val pkgNode = model.root.children[0]
        assertEquals("(default package)", pkgNode.name)
    }

    @Test
    fun packagesSortedAlphabetically() {
        val files = listOf(
            makeFile("z/pkg/Z.java", 1, 0),
            makeFile("a/pkg/A.java", 1, 1),
            makeFile("m/pkg/M.java", 1, 0),
        )
        val model = CoverageTreeModel.build(files)

        val names = model.root.children.map { it.name }
        assertEquals(listOf("a.pkg", "m.pkg", "z.pkg"), names)
    }

    private fun makeFile(path: String, total: Int, covered: Int): FileCoverageInfo =
        FileCoverageInfo(
            filePath = path,
            lineStatuses = emptyMap(),
            lineDetails = emptyMap(),
            numStatements = total,
            numCoveredStatements = covered,
            numBranches = 0,
            numCoveredBranches = 0,
            percentCovered = if (total > 0) covered.toFloat() / total else 0f,
        )
}
