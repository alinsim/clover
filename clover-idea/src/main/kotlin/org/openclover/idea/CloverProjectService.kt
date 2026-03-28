package org.openclover.idea

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Project-level service for OpenClover.
 * Replaces the old [ProjectComponent]-based ProjectPlugin.
 *
 * Manages per-project coverage state: database path, coverage model,
 * instrumentation config, and coverage refresh scheduling.
 *
 * The [coroutineScope] is provided by the IntelliJ platform and is cancelled
 * automatically when the project closes — no manual lifecycle management needed.
 */
@Service(Service.Level.PROJECT)
class CloverProjectService(
    private val project: Project,
    private val coroutineScope: CoroutineScope,
) {

    init {
        thisLogger().info("OpenClover project service initialized for: ${project.name}")
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): CloverProjectService =
            project.getService(CloverProjectService::class.java)
    }
}
