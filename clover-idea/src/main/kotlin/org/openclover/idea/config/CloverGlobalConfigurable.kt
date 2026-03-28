package org.openclover.idea.config

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import org.openclover.idea.CloverApplicationService

/**
 * Application-level settings page for OpenClover.
 * Accessible via Settings → Tools → OpenClover.
 *
 * Uses the Kotlin UI DSL v2 for declarative settings panel construction.
 */
class CloverGlobalConfigurable : BoundConfigurable("OpenClover") {

    private val config: CloverGlobalConfig
        get() = CloverApplicationService.getInstance().state

    override fun createPanel(): DialogPanel = panel {
        group("Project View") {
            row {
                checkBox("Auto-scroll to source")
                    .bindSelected(config::autoScrollToSource)
            }
            row {
                checkBox("Auto-scroll from source")
                    .bindSelected(config::autoScrollFromSource)
            }
            row {
                checkBox("Flatten packages")
                    .bindSelected(config::flattenPackages)
            }
        }
    }
}
