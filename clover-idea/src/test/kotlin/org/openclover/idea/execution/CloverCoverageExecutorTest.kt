package org.openclover.idea.execution

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

/**
 * Tests for CloverCoverageExecutor configuration and identity.
 */
class CloverCoverageExecutorTest {

    @Test
    fun executorIdIsStable() {
        assertEquals(
            "CloverCoverage",
            CloverCoverageExecutor.EXECUTOR_ID,
            "Executor ID must be stable — it's used in plugin.xml and state persistence"
        )
    }

    @Test
    fun executorHasToolWindowId() {
        val executor = CloverCoverageExecutor()
        assertNotNull(executor.toolWindowId, "Executor must have a tool window ID")
    }

    @Test
    fun executorHasActionName() {
        val executor = CloverCoverageExecutor()
        assertNotNull(executor.actionName, "Executor must have an action name")
        assertFalse(executor.actionName.isBlank(), "Action name must not be blank")
    }

    @Test
    fun executorHasIcon() {
        val executor = CloverCoverageExecutor()
        assertNotNull(executor.icon, "Executor must have an icon")
    }

    @Test
    fun executorHasDescription() {
        val executor = CloverCoverageExecutor()
        assertNotNull(executor.description, "Executor must have a description")
    }
}
