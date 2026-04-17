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
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.beans.PropertyChangeListener
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * FileEditorProvider for the TreeMap coverage visualization.
 */
class TreeMapEditorProvider : FileEditorProvider, DumbAware {
    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".clover-treemap")

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        TreeMapReportEditor(project)

    override fun getEditorTypeId(): String = "openclover-treemap"
    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

/**
 * TreeMap report editor showing coverage as a treemap visualization.
 * Each rectangle represents a file, sized by statement count and colored by coverage percentage.
 */
class TreeMapReportEditor(private val project: Project) : UserDataHolderBase(), FileEditor {

    private val treeMapPanel = TreeMapPanel(project)
    private val panel = JPanel(BorderLayout()).apply {
        val header = JBLabel("Coverage TreeMap — files sized by statements, colored by coverage").apply {
            border = JBUI.Borders.empty(8)
        }
        add(header, BorderLayout.NORTH)
        add(JBScrollPane(treeMapPanel), BorderLayout.CENTER)
    }

    override fun getComponent(): JComponent = panel
    override fun getPreferredFocusedComponent(): JComponent = treeMapPanel
    override fun getName(): String = "Coverage TreeMap"
    override fun setState(state: FileEditorState) {}
    override fun isModified(): Boolean = false
    override fun isValid(): Boolean = true
    override fun addPropertyChangeListener(listener: PropertyChangeListener) {}
    override fun removePropertyChangeListener(listener: PropertyChangeListener) {}
    override fun getFile(): VirtualFile? = null
    override fun dispose() {}
}

/**
 * Custom panel that renders a squarified treemap of file coverage.
 * Uses the SquarifiedTreeMap algorithm for better aspect ratios.
 */
private class TreeMapPanel(private val project: Project) : JPanel() {

    private var treeMapRects: List<TreeMapRect> = emptyList()
    private var fileInfoMap: Map<String, FileCoverageInfo> = emptyMap()

    init {
        preferredSize = Dimension(800, 600)
        loadData()
    }

    private fun loadData() {
        val service = CloverProjectService.getInstance(project)
        val state = service.coverageManager.state.value
        if (state is CoverageState.Loaded) {
            val files = state.fileCoverage.values.filter { it.numStatements > 0 }

            // Build treemap items
            val items = files.map { file ->
                TreeMapItem(
                    label = file.filePath,
                    size = file.numStatements.toDouble()
                )
            }

            // Compute layout
            val bounds = java.awt.Rectangle(0, 0, width.coerceAtLeast(800), height.coerceAtLeast(600))
            treeMapRects = SquarifiedTreeMap.layout(items, bounds)

            // Build lookup map for coverage info
            fileInfoMap = files.associateBy { it.filePath }
        }
    }

    override fun paintComponent(g: Graphics) {
        super.paintComponent(g)
        if (treeMapRects.isEmpty()) return

        val g2 = g as Graphics2D
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)

        for (treeMapRect in treeMapRects) {
            val file = fileInfoMap[treeMapRect.item.label] ?: continue
            val rect = treeMapRect.rect

            // Color by coverage: green (high) → yellow (mid) → red (low)
            val pct = file.percentCovered
            val color = when {
                pct >= 0.8f -> JBColor(Color(0x4C, 0xAF, 0x50), Color(0x2E, 0x7D, 0x32))
                pct >= 0.5f -> JBColor(Color(0xFF, 0xC1, 0x07), Color(0xF5, 0x7F, 0x17))
                else -> JBColor(Color(0xF4, 0x43, 0x36), Color(0xC6, 0x28, 0x28))
            }

            g2.color = color
            g2.fillRect(rect.x, rect.y, rect.width, rect.height)

            g2.color = JBColor.background()
            g2.drawRect(rect.x, rect.y, rect.width, rect.height)

            // Draw file name if rectangle is large enough
            if (rect.width > 60 && rect.height > 20) {
                g2.color = JBColor.foreground()
                val fileName = file.filePath.substringAfterLast('/')
                val pctText = "%.0f%%".format(pct * 100)
                g2.font = g2.font.deriveFont(10f)
                g2.drawString(fileName, rect.x + 4, rect.y + 14)
                if (rect.height > 30) {
                    g2.drawString(pctText, rect.x + 4, rect.y + 26)
                }
            }
        }
    }
}
