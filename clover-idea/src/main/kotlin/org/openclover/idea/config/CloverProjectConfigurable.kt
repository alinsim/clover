package org.openclover.idea.config

import com.intellij.openapi.options.BoundConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.builder.rows
import org.openclover.idea.CloverProjectService

/**
 * Project-level settings page for OpenClover.
 * Accessible via Settings → Tools → OpenClover → Project Settings.
 *
 * Uses Kotlin UI DSL v2 for declarative settings panel construction.
 */
class CloverProjectConfigurable(private val project: Project) : BoundConfigurable("Project Settings") {

    private val config: CloverProjectConfig
        get() = CloverProjectService.getInstance(project).getConfig()

    override fun createPanel(): DialogPanel = panel {
        group("General") {
            row {
                checkBox("Enable OpenClover for this project")
                    .bindSelected(config::enabled)
            }
            row {
                checkBox("Build with Clover instrumentation")
                    .bindSelected(config::buildWithClover)
            }
        }

        group("Coverage Database") {
            row("Init string (database path):") {
                textField()
                    .bindText(config::initString)
                    .comment("Path pattern for the Clover coverage database file (.db)")
            }
            row("Coverage span:") {
                textField()
                    .bindText(config::span)
                    .comment("Time window for filtering coverage data (e.g., '30s', '10m', '1h')")
            }
            row("Context filter:") {
                textField()
                    .bindText(config::contextFilterSpec)
                    .comment("Comma-separated context names to exclude (e.g., 'try, catch, finally')")
            }
        }

        group("Editor Display") {
            row {
                checkBox("Show coverage data in editor")
                    .bindSelected(config::showCoverage)
            }
            row {
                checkBox("Show gutter icons")
                    .bindSelected(config::showGutter)
            }
            row {
                checkBox("Show inline highlights")
                    .bindSelected(config::showInline)
            }
            row {
                checkBox("Show tooltips on hover")
                    .bindSelected(config::showTooltips)
            }
            row {
                checkBox("Show error marks in scrollbar")
                    .bindSelected(config::showErrorMarks)
            }
        }

        group("Auto-Refresh") {
            row {
                checkBox("Automatically refresh coverage data")
                    .bindSelected(config::autoRefresh)
            }
        }

        group("Test Filtering") {
            row {
                checkBox("Include only passed test coverage")
                    .bindSelected(config::includePassedTestCoverageOnly)
            }
            row {
                checkBox("Include failed test coverage")
                    .bindSelected(config::includeFailedTestCoverage)
            }
        }
    }
}
