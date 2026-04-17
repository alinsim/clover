package org.openclover.idea.execution

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.configurations.RunConfigurationBase
import com.intellij.execution.configurations.RunnerSettings
import com.intellij.openapi.options.SettingsEditor
import com.intellij.openapi.util.InvalidDataException
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.WriteExternalException
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.FormBuilder
import org.jdom.Element
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Run configuration extension that adds a "Enable Clover instrumentation" checkbox
 * to JUnit and Application run configuration editors.
 *
 * This allows per-configuration control of whether Clover should instrument code
 * when running that specific configuration.
 *
 * Registered in plugin.xml as `<runConfigurationExtension>`.
 */
class CloverRunConfigurationExtension : com.intellij.execution.configuration.RunConfigurationExtensionBase<RunConfigurationBase<*>>() {

    @Throws(ExecutionException::class)
    override fun patchCommandLine(
        configuration: RunConfigurationBase<*>,
        runnerSettings: RunnerSettings?,
        cmdLine: GeneralCommandLine,
        runnerId: String
    ) {
        // Future: modify command line to include Clover instrumentation flags
    }

    override fun isApplicableFor(configuration: RunConfigurationBase<*>): Boolean {
        // Apply to all configurations for now
        return true
    }

    override fun isEnabledFor(
        applicableConfiguration: RunConfigurationBase<*>,
        runnerSettings: RunnerSettings?
    ): Boolean {
        // Always enabled
        return true
    }

    override fun getEditorTitle(): String {
        return "Clover"
    }

    override fun <P : RunConfigurationBase<*>> createEditor(configuration: P): SettingsEditor<P> {
        return CloverRunConfigurationEditor()
    }

    override fun getSerializationId(): String {
        return "CloverRunConfigurationExtension"
    }

    @Throws(WriteExternalException::class)
    override fun writeExternal(runConfiguration: RunConfigurationBase<*>, element: Element) {
        val state = runConfiguration.getCopyableUserData(CLOVER_STATE_KEY)
        if (state != null && state.isCloverEnabled) {
            element.setAttribute("cloverEnabled", "true")
        }
    }

    @Throws(InvalidDataException::class)
    override fun readExternal(runConfiguration: RunConfigurationBase<*>, element: Element) {
        val state = CloverRunConfigurationState()
        state.isCloverEnabled = element.getAttributeValue("cloverEnabled") == "true"
        runConfiguration.putCopyableUserData(CLOVER_STATE_KEY, state)
    }

    companion object {
        private val CLOVER_STATE_KEY = Key.create<CloverRunConfigurationState>("CloverRunConfigurationState")

        fun getState(configuration: RunConfigurationBase<*>): CloverRunConfigurationState {
            return configuration.getCopyableUserData(CLOVER_STATE_KEY) ?: CloverRunConfigurationState()
        }

        fun setState(configuration: RunConfigurationBase<*>, state: CloverRunConfigurationState) {
            configuration.putCopyableUserData(CLOVER_STATE_KEY, state)
        }
    }
}

/**
 * State object for per-configuration Clover settings.
 */
data class CloverRunConfigurationState(
    var isCloverEnabled: Boolean = false
)

/**
 * Settings editor UI for the Clover run configuration extension.
 */
private class CloverRunConfigurationEditor<T : RunConfigurationBase<*>> : SettingsEditor<T>() {

    private val enableCheckbox = JBCheckBox("Enable Clover instrumentation for this run")
    private val panel: JPanel = FormBuilder.createFormBuilder()
        .addComponent(enableCheckbox)
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun resetEditorFrom(configuration: T) {
        val state = CloverRunConfigurationExtension.getState(configuration)
        enableCheckbox.isSelected = state.isCloverEnabled
    }

    override fun applyEditorTo(configuration: T) {
        val state = CloverRunConfigurationState(isCloverEnabled = enableCheckbox.isSelected)
        CloverRunConfigurationExtension.setState(configuration, state)
    }

    override fun createEditor(): JComponent = panel
}
