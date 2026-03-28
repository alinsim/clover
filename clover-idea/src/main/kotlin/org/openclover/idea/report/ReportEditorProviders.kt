package org.openclover.idea.report

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel
import java.awt.BorderLayout

/**
 * FileEditorProvider for the TreeMap coverage visualization.
 * Opens a treemap view when the user selects the TreeMap report action.
 *
 * TODO: Implement full treemap visualization using jtreemap library.
 * The old plugin used net.sf.jtreemap with custom rendering.
 */
class TreeMapEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".clover-treemap")

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        PlaceholderReportEditor("TreeMap Report — visualization coming soon")

    override fun getEditorTypeId(): String = "openclover-treemap"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

/**
 * FileEditorProvider for the Cloud coverage visualization.
 * Shows a tag-cloud view of classes weighted by coverage risk.
 *
 * TODO: Implement full cloud visualization.
 */
class CloudEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".clover-cloud")

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        PlaceholderReportEditor("Cloud Report — visualization coming soon")

    override fun getEditorTypeId(): String = "openclover-cloud"

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

/**
 * Placeholder editor for report views that aren't fully implemented yet.
 */
private class PlaceholderReportEditor(message: String) : UserDataHolderBase(), FileEditor {

    private val panel = JPanel(BorderLayout()).apply {
        add(JBLabel(message).apply {
            border = JBUI.Borders.empty(20)
            horizontalAlignment = JBLabel.CENTER
        }, BorderLayout.CENTER)
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = panel
    override fun getName(): String = "OpenClover Report"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile? = null
    override fun dispose() {}
}
