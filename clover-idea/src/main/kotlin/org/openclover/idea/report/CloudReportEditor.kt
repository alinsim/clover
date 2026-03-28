package org.openclover.idea.report

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.util.ui.JBUI
import org.openclover.idea.CloverProjectService
import org.openclover.idea.coverage.CoverageState
import org.openclover.idea.coverage.FileCoverageInfo
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * FileEditorProvider for the Cloud coverage visualization.
 */
class CloudEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".clover-cloud")

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        CloudReportEditor(project)

    override fun getEditorTypeId(): String = "openclover-cloud"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

/**
 * Cloud report editor showing a tag-cloud view of classes.
 * Font size indicates risk (low coverage + high complexity = large text).
 * Color indicates coverage level (green = high, red = low).
 */
class CloudReportEditor(private val project: Project) : UserDataHolderBase(), FileEditor {

    private val cloudPanel = CloudPanel(project)
    private val panel = JPanel(BorderLayout()).apply {
        val header = JBLabel("Coverage Cloud — font size = risk, color = coverage level").apply {
            border = JBUI.Borders.empty(8)
        }
        add(header, BorderLayout.NORTH)
        add(JBScrollPane(cloudPanel), BorderLayout.CENTER)
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = cloudPanel
    override fun getName(): String = "Coverage Cloud"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile? = null
    override fun dispose() {}
}

/**
 * Panel that renders a tag-cloud of files weighted by coverage risk.
 * Risk = (1 - coverage%) * statements. High risk items appear larger.
 */
private class CloudPanel(private val project: Project) : JPanel(FlowLayout(FlowLayout.CENTER, 8, 4)) {

    init {
        border = JBUI.Borders.empty(16)
        preferredSize = Dimension(800, 600)
        loadData()
    }

    private fun loadData() {
        val service = CloverProjectService.getInstance(project)
        val state = service.coverageManager.state.value
        if (state !is CoverageState.Loaded) return

        val files = state.fileCoverage.values
            .filter { it.numStatements > 0 }
            .sortedByDescending { riskScore(it) }
            .take(100) // Limit to top 100 riskiest files

        val maxRisk = files.maxOfOrNull { riskScore(it) } ?: 1f

        for (file in files) {
            val risk = riskScore(file)
            val fontSize = 10f + (risk / maxRisk) * 24f // 10px to 34px
            val pct = file.percentCovered

            val color = when {
                pct >= 0.8f -> JBColor(Color(0x2E, 0x7D, 0x32), Color(0x66, 0xBB, 0x6A))
                pct >= 0.5f -> JBColor(Color(0xF5, 0x7F, 0x17), Color(0xFF, 0xB7, 0x4D))
                else -> JBColor(Color(0xC6, 0x28, 0x28), Color(0xEF, 0x53, 0x50))
            }

            val fileName = file.filePath.substringAfterLast('/').removeSuffix(".java").removeSuffix(".groovy")
            val label = JBLabel(fileName).apply {
                font = font.deriveFont(Font.PLAIN, fontSize)
                foreground = color
                toolTipText = "${file.filePath.substringAfterLast('/')}: ${"%.0f".format(pct * 100)}% covered, ${file.numStatements} statements"
            }
            add(label)
        }
    }

    private fun riskScore(file: FileCoverageInfo): Float =
        (1f - file.percentCovered) * file.numStatements
}
