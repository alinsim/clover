package org.openclover.idea

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.util.Disposer
import org.openclover.idea.editor.CoverageEditorAnnotator

/**
 * Runs when a project is opened and initialized.
 *
 * Sets up the editor annotator (reactive to coverage state changes)
 * but does NOT load coverage or instrument code on startup.
 *
 * Coverage is only loaded when:
 * - User explicitly runs "Run with Clover Coverage"
 * - User manually clicks "Refresh Coverage" in the Tools menu
 *
 * This avoids showing stale coverage from previous Maven runs and
 * ensures normal Run/Debug is completely untouched.
 */
class CloverProjectActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val service = CloverProjectService.getInstance(project)
        if (!service.isEnabled) {
            thisLogger().info("OpenClover is disabled for project: ${project.name}")
            return
        }

        thisLogger().info("OpenClover activating for project: ${project.name}")

        // Start editor coverage annotations (reactive — only shows when coverage is loaded)
        val editorAnnotator = CoverageEditorAnnotator(project, service.coroutineScope)
        Disposer.register(service, editorAnnotator)
        editorAnnotator.start()

        // No coverage load on startup — wait for explicit user action
        // No compile task registration — instrumentation only in CloverProgramRunner
    }
}
