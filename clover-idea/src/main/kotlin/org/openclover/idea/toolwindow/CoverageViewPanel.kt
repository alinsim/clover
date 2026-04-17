package org.openclover.idea.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.openclover.idea.CloverProjectService
import org.openclover.idea.coverage.CoverageState
import org.openclover.idea.coverage.FileCoverageInfo
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Panel displayed in the OpenClover tool window.
 * Shows a summary bar with overall coverage and a table of per-file coverage.
 */
class CoverageViewPanel(
    private val project: Project,
) : Disposable {

    private val summaryLabel = JBLabel("No coverage data loaded").apply {
        border = JBUI.Borders.empty(8)
        font = font.deriveFont(13f)
    }

    private val tableModel = CoverageTableModel()
    private val table = JBTable(tableModel).apply {
        fillsViewportHeight = true
        setShowGrid(false)
        rowHeight = JBUI.scale(24)
        autoCreateRowSorter = true

        // Right-align numeric columns
        val rightRenderer = object : DefaultTableCellRenderer() {
            init { horizontalAlignment = SwingConstants.RIGHT }
        }
        for (col in 1..4) {
            columnModel.getColumn(col).cellRenderer = rightRenderer
        }
        // Coverage % with color
        columnModel.getColumn(5).cellRenderer = CoveragePercentRenderer()

    }

    init {
        // Double-click to navigate to file
        table.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val row = table.rowAtPoint(e.point)
                    if (row >= 0) {
                        val modelRow = table.convertRowIndexToModel(row)
                        val filePath = tableModel.getFilePathAt(modelRow)
                        if (filePath != null) {
                            navigateToFile(filePath)
                        }
                    }
                }
            }
        })
    }

    private val panel = JPanel(BorderLayout()).apply {
        add(summaryLabel, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    val component: JComponent get() = panel

    init {
        Disposer.register(CloverProjectService.getInstance(project), this)

        val service = CloverProjectService.getInstance(project)
        service.coroutineScope.launch {
            service.coverageManager.state.collectLatest { state ->
                // Swing component updates must happen on EDT
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        updateView(state)
                    }
                }
            }
        }
    }

    private fun updateView(state: CoverageState) {
        when (state) {
            is CoverageState.Empty -> {
                summaryLabel.text = "No coverage data loaded. Run tests with Clover instrumentation."
                tableModel.setFiles(emptyList())
            }
            is CoverageState.Loading -> {
                summaryLabel.text = "Loading coverage data..."
            }
            is CoverageState.Error -> {
                summaryLabel.text = "Error loading coverage: ${state.message}"
                tableModel.setFiles(emptyList())
            }
            is CoverageState.Loaded -> {
                val metrics = state.projectInfo.metrics
                val stmtPct = if (metrics.numStatements > 0) {
                    "%.1f%%".format(metrics.pcCoveredElements * 100)
                } else "N/A"
                summaryLabel.text =
                    "Coverage: $stmtPct | " +
                    "${metrics.numCoveredStatements}/${metrics.numStatements} statements | " +
                    "${state.fileCoverage.size} files"

                val sortedFiles = state.fileCoverage.values
                    .sortedBy { it.percentCovered }
                tableModel.setFiles(sortedFiles.toList())
            }
        }
    }

    private fun navigateToFile(filePath: String) {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return
        val descriptor = OpenFileDescriptor(project, virtualFile)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }

    override fun dispose() {}
}

/**
 * Table model for per-file coverage data.
 * Columns: File | Statements | Covered | Branches | Covered | Coverage %
 */
private class CoverageTableModel : AbstractTableModel() {

    private var files: List<FileCoverageInfo> = emptyList()

    private val columnNames = arrayOf("File", "Stmts", "Covered", "Branches", "Br. Covered", "Coverage")

    fun setFiles(newFiles: List<FileCoverageInfo>) {
        files = newFiles
        fireTableDataChanged()
    }

    fun getFilePathAt(row: Int): String? {
        if (row < 0 || row >= files.size) return null
        return files[row].filePath
    }

    override fun getRowCount(): Int = files.size
    override fun getColumnCount(): Int = columnNames.size
    override fun getColumnName(column: Int): String = columnNames[column]

    override fun getColumnClass(columnIndex: Int): Class<*> = when (columnIndex) {
        0 -> String::class.java
        5 -> Float::class.java
        else -> Int::class.java
    }

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val file = files[rowIndex]
        return when (columnIndex) {
            0 -> file.filePath.substringAfterLast('/')
            1 -> file.numStatements
            2 -> file.numCoveredStatements
            3 -> file.numBranches
            4 -> file.numCoveredBranches
            5 -> file.percentCovered
            else -> ""
        }
    }
}

/**
 * Renders coverage percentage with color coding.
 */
private class CoveragePercentRenderer : DefaultTableCellRenderer() {
    init {
        horizontalAlignment = SwingConstants.RIGHT
    }

    override fun getTableCellRendererComponent(
        table: JTable,
        value: Any?,
        isSelected: Boolean,
        hasFocus: Boolean,
        row: Int,
        column: Int,
    ): Component {
        val component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        val pct = (value as? Float) ?: 0f
        text = "%.1f%%".format(pct * 100)
        if (!isSelected) {
            foreground = when {
                pct >= 0.8f -> JBColor(java.awt.Color(0x2E, 0x7D, 0x32), java.awt.Color(0x66, 0xBB, 0x6A))
                pct >= 0.5f -> JBColor(java.awt.Color(0xF5, 0x7F, 0x17), java.awt.Color(0xFF, 0xB7, 0x4D))
                else -> JBColor(java.awt.Color(0xC6, 0x28, 0x28), java.awt.Color(0xEF, 0x53, 0x50))
            }
        }
        return component
    }
}
