package org.openclover.idea

import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.thisLogger

/**
 * Application-level service for OpenClover.
 * Replaces the old [ApplicationComponent]-based CloverPlugin.
 *
 * Manages global plugin state: settings, version info, shared resources.
 */
@Service(Service.Level.APP)
class CloverApplicationService {

    init {
        thisLogger().info("OpenClover plugin initialized")
    }

    companion object {
        const val PLUGIN_ID = "org.openclover.idea"
    }
}
