package org.openclover.idea.execution

import com.intellij.execution.configurations.RunnerSettings
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

/**
 * Tests for CloverRunConfigurationExtension.
 */
class CloverRunConfigurationExtensionTest {

    private val extension = CloverRunConfigurationExtension()

    @Test
    fun extensionCanBeInstantiated() {
        assertNotNull(extension)
    }

    @Test
    fun isApplicableForReturnsTrue() {
        val mockConfig = mock<com.intellij.execution.configurations.RunConfigurationBase<*>>()
        assertTrue(
            extension.isApplicableFor(mockConfig),
            "Extension should be applicable for all run configurations"
        )
    }

    @Test
    fun isEnabledForReturnsTrueByDefault() {
        val mockConfig = mock<com.intellij.execution.configurations.RunConfigurationBase<*>>()
        val mockSettings = mock<RunnerSettings>()
        assertTrue(
            extension.isEnabledFor(mockConfig, mockSettings),
            "Extension should be enabled by default"
        )
    }

    @Test
    fun cloverEnabledDefaultsToFalse() {
        val state = CloverRunConfigurationState()
        assertFalse(
            state.isCloverEnabled,
            "Clover instrumentation should be disabled by default"
        )
    }

    @Test
    fun cloverEnabledCanBeToggled() {
        val state = CloverRunConfigurationState()

        state.isCloverEnabled = true
        assertTrue(state.isCloverEnabled)

        state.isCloverEnabled = false
        assertFalse(state.isCloverEnabled)
    }
}
