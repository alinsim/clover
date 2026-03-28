package org.openclover.idea

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.diagnostic.thisLogger
import org.openclover.idea.config.CloverGlobalConfig

/**
 * Application-level service for OpenClover.
 * Replaces the old `CloverPlugin` [ApplicationComponent].
 *
 * Responsibilities:
 * - Global configuration persistence
 * - Clover runtime logging bridge
 * - Plugin version information
 */
@Service(Service.Level.APP)
@State(
    name = "OpenCloverGlobalSettings",
    storages = [Storage("openclover.xml")],
)
class CloverApplicationService : PersistentStateComponent<CloverGlobalConfig> {

    private var config = CloverGlobalConfig()

    init {
        initializeLogging()
        thisLogger().info("OpenClover plugin initialized")
    }

    override fun getState(): CloverGlobalConfig = config

    override fun loadState(state: CloverGlobalConfig) {
        config = state
    }

    /**
     * Bridge Clover's runtime Logger to IntelliJ's diagnostic logger.
     * The old plugin set a custom LoggerFactory; we do the same here
     * so that clover-core log messages appear in idea.log.
     */
    private fun initializeLogging() {
        try {
            org.openclover.runtime.Logger.setFactory { category ->
                CloverLoggingBridge(
                    com.intellij.openapi.diagnostic.Logger.getInstance(category),
                )
            }
        } catch (e: Exception) {
            thisLogger().warn("Failed to initialize Clover logging bridge", e)
        }
    }

    companion object {
        const val PLUGIN_ID = "org.openclover.idea"

        @JvmStatic
        fun getInstance(): CloverApplicationService =
            ApplicationManager.getApplication().getService(CloverApplicationService::class.java)
    }
}

/**
 * Bridges Clover's internal [org.openclover.runtime.Logger] to IntelliJ's
 * diagnostic [com.intellij.openapi.diagnostic.Logger].
 */
private class CloverLoggingBridge(
    private val ideaLogger: com.intellij.openapi.diagnostic.Logger,
) : org.openclover.runtime.Logger() {

    override fun log(level: Int, msg: String?, t: Throwable?) {
        when (level) {
            LOG_VERBOSE, LOG_DEBUG -> {
                ideaLogger.debug(msg)
                t?.let { ideaLogger.debug(it) }
            }
            LOG_INFO -> {
                ideaLogger.info(msg)
                t?.let { ideaLogger.info(it) }
            }
            LOG_WARN -> ideaLogger.warn(msg, t)
            LOG_ERR -> {
                if (t != null) ideaLogger.error(msg, t) else ideaLogger.error(msg ?: "")
            }
            else -> ideaLogger.debug("<unknown log level> $msg")
        }
    }
}
