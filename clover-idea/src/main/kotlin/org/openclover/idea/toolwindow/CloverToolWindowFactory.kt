package org.openclover.idea.toolwindow

import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.content.ContentFactory

/**
 * Factory for creating the OpenClover tool window.
 * Registered in plugin.xml as a toolWindow extension.
 *
 * DumbAware allows the tool window to be available during indexing.
 */
class CloverToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val coveragePanel = CoverageViewPanel(project)
        val content = ContentFactory.getInstance().createContent(
            coveragePanel.component,
            "Coverage",
            false,
        )
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean = true
}
