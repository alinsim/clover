package org.openclover.idea.statusbar

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.util.Consumer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.openclover.idea.CloverProjectService
import org.openclover.idea.coverage.CoverageState
import java.awt.event.MouseEvent

/**
 * Status bar widget showing overall project coverage percentage.
 * Clicking opens the OpenClover tool window.
 */
class CoverageStatusBarWidget(private val project: Project) : StatusBarWidget,
    StatusBarWidget.TextPresentation {

    private var statusBar: StatusBar? = null
    private var currentText = "Clover: —"

    override fun ID(): String = CoverageStatusBarWidgetFactory.WIDGET_ID

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar

        val service = CloverProjectService.getInstance(project)
        service.coroutineScope.launch {
            service.coverageManager.state.collectLatest { state ->
                currentText = when (state) {
                    is CoverageState.Loaded -> {
                        val pct = state.projectInfo.metrics.pcCoveredElements * 100
                        "Clover: ${"%.1f".format(pct)}%"
                    }
                    is CoverageState.Loading -> "Clover: loading..."
                    is CoverageState.Error -> "Clover: error"
                    is CoverageState.Empty -> "Clover: —"
                }
                statusBar.updateWidget(ID())
            }
        }
    }

    override fun dispose() {
        statusBar = null
    }

    override fun getPresentation(): StatusBarWidget.WidgetPresentation = this

    override fun getText(): String = currentText

    override fun getAlignment(): Float = 0f

    override fun getTooltipText(): String = "OpenClover project coverage"

    override fun getClickConsumer(): Consumer<MouseEvent> = Consumer {
        ToolWindowManager.getInstance(project).getToolWindow("OpenClover")?.activate(null)
    }
}
