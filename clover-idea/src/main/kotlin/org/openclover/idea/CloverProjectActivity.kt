package org.openclover.idea

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import org.openclover.idea.build.CloverCompileTask
import org.openclover.idea.editor.CoverageEditorAnnotator

/**
 * Runs when a project is opened and initialized.
 * Replaces the old `StartupManager.runWhenProjectIsInitialized` callback.
 *
 * This is a suspend function — it runs in the project's coroutine scope
 * and can perform async I/O without blocking the EDT.
 */
class CloverProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val service = CloverProjectService.getInstance(project)
        if (!service.isEnabled) {
            thisLogger().info("OpenClover is disabled for project: ${project.name}")
            return
        }

        thisLogger().info("OpenClover activating for project: ${project.name}")

        // Start editor coverage annotations
        val editorAnnotator = CoverageEditorAnnotator(project, service.coroutineScope)
        Disposer.register(service, editorAnnotator)
        editorAnnotator.start()

        // Load coverage data
        val coverageManager = service.coverageManager
        coverageManager.reload()

        // Start auto-refresh if configured
        if (service.getConfig().autoRefresh) {
            coverageManager.startAutoRefresh()
        }

        // Register build system hooks (CompilerManager is an EDT API)
        if (service.isBuildWithClover) {
            ApplicationManager.getApplication().invokeLater {
                if (!project.isDisposed) {
                    CloverCompileTask.register(project)
                }
            }
        }
    }
}
