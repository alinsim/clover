package org.openclover.idea

import com.intellij.ide.BrowserUtil
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.dsl.builder.panel
import javax.swing.JComponent

/**
 * About dialog showing OpenClover version information and links.
 */
class AboutDialog(project: Project?) : DialogWrapper(project) {

    init {
        title = "About OpenClover"
        init()
    }

    override fun createCenterPanel(): JComponent {
        val plugin = PluginManagerCore.getPlugin(PluginId.getId("org.openclover.idea"))
        val pluginVersion = plugin?.version ?: "unknown"
        val cloverVersion = getCloverVersion()
        val javaVersion = System.getProperty("java.version")
        val javaVendor = System.getProperty("java.vendor")

        return panel {
            row {
                label("<html><h2>OpenClover IntelliJ IDEA Plugin</h2></html>")
            }
            row {
                label("Plugin Version: $pluginVersion")
            }
            row {
                label("OpenClover Version: $cloverVersion")
            }
            separator()
            row {
                label("Java Runtime: $javaVersion ($javaVendor)")
            }
            separator()
            row {
                link("Documentation") {
                    BrowserUtil.browse("https://openclover.org/documentation")
                }
            }
            row {
                link("GitHub Repository") {
                    BrowserUtil.browse("https://github.com/openclover/clover")
                }
            }
            row {
                link("Issue Tracker") {
                    BrowserUtil.browse("https://github.com/openclover/clover/issues")
                }
            }
        }
    }

    private fun getCloverVersion(): String {
        return try {
            // Read version from CloverVersionInfo class
            val versionClass = Class.forName("org.openclover.core.CloverVersionInfo")
            val method = versionClass.getMethod("formatVersionInfo")
            method.invoke(null) as? String ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }
}
