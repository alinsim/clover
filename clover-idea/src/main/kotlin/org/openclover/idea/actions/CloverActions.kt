package org.openclover.idea.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.thisLogger
import org.openclover.idea.CloverProjectService

/**
 * Base class for OpenClover actions that require a project context.
 * Provides common infrastructure: project service access, enabled state based on
 * whether Clover is enabled for the project.
 */
abstract class CloverProjectAction : AnAction() {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        val project = e.project
        if (project == null || project.isDisposed) {
            e.presentation.isEnabled = false
            return
        }
        val service = CloverProjectService.getInstance(project)
        e.presentation.isEnabled = service.isEnabled
    }
}

/** Refresh coverage data from the database. */
class RefreshCoverageAction : CloverProjectAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        thisLogger().info("Refreshing coverage data for: ${project.name}")
        CloverProjectService.getInstance(project).coverageManager.reload()
    }
}

/** Toggle Clover instrumentation on/off for the project. */
class ToggleInstrumentationAction : CloverProjectAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val config = CloverProjectService.getInstance(project).getConfig()
        e.presentation.isEnabled = true
        e.presentation.text = if (config.buildWithClover) "Disable Clover Instrumentation" else "Enable Clover Instrumentation"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val config = CloverProjectService.getInstance(project).getConfig()
        config.buildWithClover = !config.buildWithClover
        thisLogger().info("Clover instrumentation ${if (config.buildWithClover) "enabled" else "disabled"} for: ${project.name}")
    }
}

/** Toggle coverage display in editors. */
class ToggleCoverageDisplayAction : CloverProjectAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val config = CloverProjectService.getInstance(project).getConfig()
        e.presentation.isEnabled = true
        e.presentation.text = if (config.showCoverage) "Hide Coverage" else "Show Coverage"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val config = CloverProjectService.getInstance(project).getConfig()
        config.showCoverage = !config.showCoverage
    }
}

/** Toggle gutter coverage icons. */
class ToggleGutterAction : CloverProjectAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val config = CloverProjectService.getInstance(project).getConfig()
        e.presentation.isEnabled = true
        e.presentation.text = if (config.showGutter) "Hide Gutter Icons" else "Show Gutter Icons"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val config = CloverProjectService.getInstance(project).getConfig()
        config.showGutter = !config.showGutter
    }
}

/** Toggle auto-refresh of coverage data. */
class ToggleAutoRefreshAction : CloverProjectAction() {
    override fun update(e: AnActionEvent) {
        val project = e.project ?: return
        val service = CloverProjectService.getInstance(project)
        val config = service.getConfig()
        e.presentation.isEnabled = true
        e.presentation.text = if (config.autoRefresh) "Disable Auto-Refresh" else "Enable Auto-Refresh"
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val service = CloverProjectService.getInstance(project)
        val config = service.getConfig()
        config.autoRefresh = !config.autoRefresh
        if (config.autoRefresh) {
            service.coverageManager.startAutoRefresh()
        } else {
            service.coverageManager.stopAutoRefresh()
        }
    }
}

/** Clean coverage data (delete database). */
class CleanCoverageAction : CloverProjectAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val config = CloverProjectService.getInstance(project).getConfig()
        val initString = config.initString
        if (initString.isNotBlank()) {
            val dbFile = java.io.File(initString)
            if (dbFile.exists() && dbFile.delete()) {
                thisLogger().info("Deleted coverage database: $initString")
                CloverProjectService.getInstance(project).coverageManager.reload()
            }
        }
    }
}

/** Show the About dialog. */
class AboutAction : AnAction() {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

    override fun actionPerformed(e: AnActionEvent) {
        // TODO: implement About dialog
        thisLogger().info("OpenClover About dialog (not yet implemented)")
    }
}
