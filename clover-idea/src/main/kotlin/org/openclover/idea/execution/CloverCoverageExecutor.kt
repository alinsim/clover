package org.openclover.idea.execution

import com.intellij.execution.Executor
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindowId
import javax.swing.Icon

/**
 * Custom executor for "Run with Clover Coverage".
 *
 * Appears as a separate button in the Run toolbar alongside Run and Debug.
 * When used, it triggers the [CloverProgramRunner] which instruments
 * sources, injects the Clover runtime, and delegates to the standard runner.
 *
 * Registered in plugin.xml as an `<executor>` extension.
 */
class CloverCoverageExecutor : Executor() {

    override fun getToolWindowId(): String = ToolWindowId.RUN

    override fun getToolWindowIcon(): Icon = ICON

    override fun getIcon(): Icon = ICON

    override fun getDisabledIcon(): Icon = ICON

    override fun getDescription(): String = "Run with OpenClover code coverage"

    override fun getActionName(): String = "Run with Clover"

    override fun getId(): String = EXECUTOR_ID

    override fun getStartActionText(): String = "Run with Clover Coverage"

    override fun getContextActionId(): String = "RunWithCloverCoverage"

    override fun getHelpId(): String? = null

    companion object {
        const val EXECUTOR_ID = "CloverCoverage"

        private val ICON: Icon = IconLoader.getIcon("/icons/svg/clover.svg", CloverCoverageExecutor::class.java)
    }
}
