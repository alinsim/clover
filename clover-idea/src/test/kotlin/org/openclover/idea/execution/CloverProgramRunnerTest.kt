package org.openclover.idea.execution

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Tests for CloverProgramRunner core behavior.
 */
class CloverProgramRunnerTest {

    private val runner = CloverProgramRunner()

    @Test
    fun canRunReturnsTrueForCloverExecutor() {
        val mockProfile = mock<com.intellij.execution.configurations.RunProfile>()
        assertTrue(
            runner.canRun(CloverCoverageExecutor.EXECUTOR_ID, mockProfile),
            "Must return true for CloverCoverage executor"
        )
    }

    @Test
    fun canRunReturnsFalseForRunExecutor() {
        val mockProfile = mock<com.intellij.execution.configurations.RunProfile>()
        assertFalse(
            runner.canRun("Run", mockProfile),
            "Must return false for standard Run executor"
        )
    }

    @Test
    fun canRunReturnsFalseForDebugExecutor() {
        val mockProfile = mock<com.intellij.execution.configurations.RunProfile>()
        assertFalse(
            runner.canRun("Debug", mockProfile),
            "Must return false for Debug executor"
        )
    }

    @Test
    fun runnerIdIsStable() {
        assertNotNull(runner.runnerId)
        assertTrue(runner.runnerId == CloverProgramRunner.RUNNER_ID)
    }
}
