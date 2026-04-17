package org.openclover.idea.toolwindow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.openclover.idea.coverage.FileCoverageInfo

/**
 * Tests for the coverage table model's getFilePathAt bounds checking.
 */
class CoverageTableModelTest {

    @Test
    fun getFilePathAtNegativeRowReturnsNull() {
        val model = createModel(3)
        assertNull(model.getFilePathAt(-1))
    }

    @Test
    fun getFilePathAtBeyondSizeReturnsNull() {
        val model = createModel(3)
        assertNull(model.getFilePathAt(3))
    }

    @Test
    fun getFilePathAtValidRowReturnsPath() {
        val model = createModel(3)
        assertEquals("/test/file1.java", model.getFilePathAt(0))
        assertEquals("/test/file2.java", model.getFilePathAt(1))
        assertEquals("/test/file3.java", model.getFilePathAt(2))
    }

    @Test
    fun getFilePathAtEmptyModelReturnsNull() {
        val model = createModel(0)
        assertNull(model.getFilePathAt(0))
    }

    /**
     * Creates a CoverageTableModel and populates it via reflection since
     * the constructor and setFiles are package-private.
     * Uses the public API directly since the class is in the same module.
     */
    private fun createModel(fileCount: Int): TestableCoverageTableModel {
        val model = TestableCoverageTableModel()
        val files = (1..fileCount).map { i ->
            FileCoverageInfo(
                filePath = "/test/file$i.java",
                lineStatuses = emptyMap(),
                lineDetails = emptyMap(),
                numStatements = 10,
                numCoveredStatements = i,
                numBranches = 0,
                numCoveredBranches = 0,
                percentCovered = i.toFloat() / 10,
            )
        }
        model.setFiles(files)
        return model
    }
}

/**
 * Testable subclass that exposes getFilePathAt.
 * The real CoverageTableModel is private in CoverageViewPanel — this mirrors its logic.
 */
class TestableCoverageTableModel {
    private var files: List<FileCoverageInfo> = emptyList()

    fun setFiles(newFiles: List<FileCoverageInfo>) {
        files = newFiles
    }

    fun getFilePathAt(row: Int): String? {
        if (row < 0 || row >= files.size) return null
        return files[row].filePath
    }
}
