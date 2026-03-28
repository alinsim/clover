package org.openclover.idea.testexplorer

import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.openclover.core.api.registry.ClassInfo
import org.openclover.core.api.registry.TestCaseInfo
import org.openclover.idea.CloverProjectService
import org.openclover.idea.coverage.CoverageState
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTable
import javax.swing.SwingConstants
import javax.swing.table.AbstractTableModel
import javax.swing.table.DefaultTableCellRenderer

/**
 * Panel for the Test Explorer tool window.
 * Shows a table of test cases with their pass/fail status and execution time.
 *
 * Loads test case information from the Clover coverage database when available.
 */
class TestExplorerPanel(private val project: Project) {

    private val summaryLabel = JBLabel("No coverage data loaded").apply {
        border = JBUI.Borders.empty(8)
        font = font.deriveFont(13f)
    }

    private val tableModel = TestCaseTableModel()
    private val table = JBTable(tableModel).apply {
        fillsViewportHeight = true
        setShowGrid(false)
        rowHeight = JBUI.scale(24)
        autoCreateRowSorter = true

        // Status column with color
        columnModel.getColumn(0).cellRenderer = StatusRenderer()
        columnModel.getColumn(0).preferredWidth = 60
        columnModel.getColumn(0).maxWidth = 80

        // Duration right-aligned
        columnModel.getColumn(3).cellRenderer = object : DefaultTableCellRenderer() {
            init { horizontalAlignment = SwingConstants.RIGHT }
        }
    }

    private val panel = JPanel(BorderLayout()).apply {
        add(summaryLabel, BorderLayout.NORTH)
        add(JBScrollPane(table), BorderLayout.CENTER)
    }

    val component: JComponent get() = panel

    init {
        val service = CloverProjectService.getInstance(project)
        service.coroutineScope.launch {
            service.coverageManager.state.collectLatest { state ->
                updateView(state)
            }
        }
    }

    private fun updateView(state: CoverageState) {
        when (state) {
            is CoverageState.Empty -> {
                summaryLabel.text = "No coverage data loaded. Run tests with Clover instrumentation."
                tableModel.setTestCases(emptyList())
            }
            is CoverageState.Loading -> {
                summaryLabel.text = "Loading test data..."
            }
            is CoverageState.Error -> {
                summaryLabel.text = "Error: ${state.message}"
                tableModel.setTestCases(emptyList())
            }
            is CoverageState.Loaded -> {
                val testCases = collectTestCases(state)
                val passed = testCases.count { it.status == TestStatus.PASSED }
                val failed = testCases.count { it.status == TestStatus.FAILED }
                val errored = testCases.count { it.status == TestStatus.ERROR }

                summaryLabel.text =
                    "Tests: ${testCases.size} | " +
                    "Passed: $passed | " +
                    "Failed: $failed | " +
                    "Errors: $errored"

                tableModel.setTestCases(testCases)
            }
        }
    }

    private fun collectTestCases(state: CoverageState.Loaded): List<TestCaseRow> {
        val rows = mutableListOf<TestCaseRow>()

        val testModel = state.database.testOnlyModel ?: return rows
        for (pkg in testModel.allPackages) {
            for (fileInfo in pkg.files) {
                for (classInfo in fileInfo.classes) {
                    for (testCase in classInfo.testCases) {
                        rows.add(
                            TestCaseRow(
                                status = when {
                                    testCase.isHasResult && testCase.isSuccess -> TestStatus.PASSED
                                    testCase.isHasResult && testCase.isError -> TestStatus.ERROR
                                    testCase.isHasResult -> TestStatus.FAILED
                                    else -> TestStatus.UNKNOWN
                                },
                                className = classInfo.name,
                                methodName = testCase.testName ?: "unknown",
                                durationMs = testCase.duration ?: 0.0,
                            ),
                        )
                    }
                }
            }
        }

        return rows.sortedWith(compareBy({ it.status.ordinal }, { it.className }, { it.methodName }))
    }
}

enum class TestStatus { FAILED, ERROR, UNKNOWN, PASSED }

data class TestCaseRow(
    val status: TestStatus,
    val className: String,
    val methodName: String,
    val durationMs: Double,
)

private class TestCaseTableModel : AbstractTableModel() {
    private var testCases: List<TestCaseRow> = emptyList()
    private val columns = arrayOf("Status", "Class", "Method", "Duration")

    fun setTestCases(cases: List<TestCaseRow>) {
        testCases = cases
        fireTableDataChanged()
    }

    override fun getRowCount(): Int = testCases.size
    override fun getColumnCount(): Int = columns.size
    override fun getColumnName(column: Int): String = columns[column]

    override fun getValueAt(rowIndex: Int, columnIndex: Int): Any {
        val tc = testCases[rowIndex]
        return when (columnIndex) {
            0 -> tc.status
            1 -> tc.className
            2 -> tc.methodName
            3 -> if (tc.durationMs > 0) "%.1fms".format(tc.durationMs) else "-"
            else -> ""
        }
    }
}

private class StatusRenderer : DefaultTableCellRenderer() {
    init { horizontalAlignment = SwingConstants.CENTER }

    override fun getTableCellRendererComponent(
        table: JTable, value: Any?, isSelected: Boolean, hasFocus: Boolean, row: Int, column: Int,
    ): Component {
        val comp = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column)
        val status = value as? TestStatus ?: TestStatus.UNKNOWN
        text = when (status) {
            TestStatus.PASSED -> "PASS"
            TestStatus.FAILED -> "FAIL"
            TestStatus.ERROR -> "ERR"
            TestStatus.UNKNOWN -> "?"
        }
        if (!isSelected) {
            foreground = when (status) {
                TestStatus.PASSED -> JBColor(Color(0x2E, 0x7D, 0x32), Color(0x66, 0xBB, 0x6A))
                TestStatus.FAILED -> JBColor(Color(0xC6, 0x28, 0x28), Color(0xEF, 0x53, 0x50))
                TestStatus.ERROR -> JBColor(Color(0xE6, 0x51, 0x00), Color(0xFF, 0x8A, 0x65))
                TestStatus.UNKNOWN -> JBColor.GRAY
            }
        }
        return comp
    }
}
