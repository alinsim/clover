package org.openclover.idea.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

/**
 * Factory for the coverage status bar widget.
 * Registered in plugin.xml as a statusBarWidgetFactory extension.
 */
class CoverageStatusBarWidgetFactory : StatusBarWidgetFactory {

    override fun getId(): String = WIDGET_ID

    override fun getDisplayName(): String = "OpenClover Coverage"

    override fun createWidget(project: Project): StatusBarWidget =
        CoverageStatusBarWidget(project)

    override fun isAvailable(project: Project): Boolean = true

    companion object {
        const val WIDGET_ID = "OpenCloverCoverage"
    }
}
