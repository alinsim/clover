package org.openclover.idea.build

import com.intellij.execution.ExecutionListener
import com.intellij.execution.process.ProcessHandler
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import org.openclover.idea.CloverProjectService
import org.openclover.idea.execution.CloverCoverageExecutor

/**
 * Listens for test/process execution completion and automatically reloads
 * coverage data from the Clover database.
 *
 * Only reloads when:
 * - The process was run via the Clover coverage executor, OR
 * - The process was a standard "Run" and Clover is enabled (to catch Maven/Gradle runs)
 */
class CoverageReloadListener(private val project: Project) : ExecutionListener {

    override fun processTerminated(
        executorId: String,
        env: ExecutionEnvironment,
        handler: ProcessHandler,
        exitCode: Int,
    ) {
        val service = CloverProjectService.getInstance(project)
        if (!service.isEnabled) return

        // Only reload for Clover coverage runs or standard Run executor
        if (executorId == CloverCoverageExecutor.EXECUTOR_ID || executorId == "Run") {
            thisLogger().info("Process terminated (executor=$executorId). Reloading coverage.")
            service.coverageManager.reload()
        }
    }
}
