package org.openclover.idea.testexplorer

import com.intellij.openapi.project.Project
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.openclover.idea.CloverProjectService
import org.openclover.idea.coverage.CoverageState
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Panel for the Test Explorer tool window.
 * Displays test results with per-test coverage information.
 *
 * TODO: Implement full UI with:
 * - JBTable or Tree showing test cases
 * - Coverage contribution per test
 * - Integration with per-test coverage data (requires globalSliceStart/End)
 */
class TestExplorerPanel(private val project: Project) {

    private val statusLabel = JBLabel("Test coverage data requires per-test recording (globalSliceStart/End)").apply {
        border = JBUI.Borders.empty(8)
    }

    private val panel = JPanel(BorderLayout()).apply {
        add(statusLabel, BorderLayout.NORTH)
    }

    val component: JComponent get() = panel

    init {
        val service = CloverProjectService.getInstance(project)
        service.coroutineScope.launch {
            service.coverageManager.state.collectLatest { state ->
                when (state) {
                    is CoverageState.Loaded -> {
                        val metrics = state.projectInfo.metrics
                        statusLabel.text =
                            "Tests: ${metrics.numTests} | " +
                            "Passed: ${metrics.numTestPasses} | " +
                            "Failed: ${metrics.numTestFailures} | " +
                            "Errors: ${metrics.numTestErrors}"
                    }
                    is CoverageState.Loading -> statusLabel.text = "Loading test data..."
                    is CoverageState.Error -> statusLabel.text = "Error: ${state.message}"
                    is CoverageState.Empty -> statusLabel.text = "No coverage data loaded"
                }
            }
        }
    }
}
