package org.openclover.idea.testexplorer

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for the Test Explorer tool window.
 * Shows test execution results with per-test coverage data.
 *
 * TODO: Implement full test explorer UI with:
 * - Test case tree (packages → classes → methods)
 * - Per-test coverage mapping (which test covers which code)
 * - Test pass/fail status with coverage contribution
 * - Click-to-navigate to test source
 */
class TestExplorerToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = TestExplorerPanel(project)
        val content = ContentFactory.getInstance().createContent(
            panel.component,
            "Test Coverage",
            false,
        )
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
