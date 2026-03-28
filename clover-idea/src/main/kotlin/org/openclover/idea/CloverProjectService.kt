package org.openclover.idea

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import kotlinx.coroutines.CoroutineScope
import org.openclover.idea.config.CloverProjectConfig
import org.openclover.idea.coverage.CoverageManager

/**
 * Project-level service for OpenClover.
 * Replaces the old `ProjectPlugin` [ProjectComponent].
 *
 * Responsibilities:
 * - Per-project configuration persistence (workspace file)
 * - Coverage data lifecycle via [CoverageManager]
 * - Feature flag management
 *
 * The [coroutineScope] is provided by the platform and cancelled automatically
 * when the project closes — no manual lifecycle management needed.
 */
@Service(Service.Level.PROJECT)
@State(
    name = "OpenCloverProjectSettings",
    storages = [Storage(StoragePathMacros.WORKSPACE_FILE)],
)
class CloverProjectService(
    val project: Project,
    val coroutineScope: CoroutineScope,
) : PersistentStateComponent<CloverProjectConfig>, Disposable {

    private var config = CloverProjectConfig()

    /** Coverage data manager — loads and refreshes coverage from the Clover database. */
    val coverageManager: CoverageManager by lazy {
        CoverageManager(project, coroutineScope, config).also {
            Disposer.register(this, it)
        }
    }

    val isEnabled: Boolean get() = config.enabled
    val isBuildWithClover: Boolean get() = config.buildWithClover

    override fun getState(): CloverProjectConfig = config

    override fun loadState(state: CloverProjectConfig) {
        config = state
        thisLogger().info("Loaded OpenClover config for project: ${project.name}")
    }

    fun getConfig(): CloverProjectConfig = config

    override fun dispose() {
        thisLogger().info("OpenClover project service disposed for: ${project.name}")
    }

    companion object {
        @JvmStatic
        fun getInstance(project: Project): CloverProjectService =
            project.getService(CloverProjectService::class.java)
    }
}
