package org.openclover.idea.toolwindow

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.openclover.idea.CloverProjectService
import org.openclover.idea.coverage.CoverageState
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeCellRenderer
import javax.swing.tree.DefaultTreeModel

/**
 * Tree view panel for the coverage tool window.
 * Shows packages → files with coverage percentages at each level.
 */
class CoverageTreePanel(private val project: Project) : Disposable {

    private val summaryLabel = JBLabel("No coverage data loaded").apply {
        border = JBUI.Borders.empty(8)
        font = font.deriveFont(13f)
    }

    private val treeModel = DefaultTreeModel(DefaultMutableTreeNode("Project"))
    private val tree = Tree(treeModel).apply {
        isRootVisible = false
        showsRootHandles = true
        cellRenderer = CoverageTreeCellRenderer()

        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    val path = getPathForLocation(e.x, e.y) ?: return
                    val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
                    val treeNode = node.userObject as? CoverageTreeModel.TreeNode ?: return
                    if (!treeNode.isPackage && treeNode.filePath != null) {
                        navigateToFile(treeNode.filePath)
                    }
                }
            }
        })
    }

    private val panel = JPanel(BorderLayout()).apply {
        add(summaryLabel, BorderLayout.NORTH)
        add(JBScrollPane(tree), BorderLayout.CENTER)
    }

    val component: JComponent get() = panel

    init {
        Disposer.register(CloverProjectService.getInstance(project), this)

        val service = CloverProjectService.getInstance(project)
        service.coroutineScope.launch {
            service.coverageManager.state.collectLatest { state ->
                ApplicationManager.getApplication().invokeLater {
                    if (!project.isDisposed) {
                        updateTree(state)
                    }
                }
            }
        }
    }

    private fun updateTree(state: CoverageState) {
        when (state) {
            is CoverageState.Empty -> {
                summaryLabel.text = "No coverage data loaded."
                treeModel.setRoot(DefaultMutableTreeNode("Project"))
            }
            is CoverageState.Loading -> {
                summaryLabel.text = "Loading coverage data..."
            }
            is CoverageState.Error -> {
                summaryLabel.text = "Error: ${state.message}"
            }
            is CoverageState.Loaded -> {
                val metrics = state.projectInfo.metrics
                val pct = metrics.pcCoveredElements
                summaryLabel.text = if (pct >= 0) {
                    "Coverage: ${"%.1f".format(pct * 100)}% | ${state.fileCoverage.size} files"
                } else {
                    "Coverage: N/A | ${state.fileCoverage.size} files"
                }

                val model = CoverageTreeModel.build(state.fileCoverage.values.toList())
                val rootNode = buildSwingTree(model.root)
                treeModel.setRoot(rootNode)

                // Expand all package nodes
                for (i in 0 until tree.rowCount) {
                    tree.expandRow(i)
                }
            }
        }
    }

    private fun buildSwingTree(node: CoverageTreeModel.TreeNode): DefaultMutableTreeNode {
        val swingNode = DefaultMutableTreeNode(node)
        for (child in node.children) {
            swingNode.add(buildSwingTree(child))
        }
        return swingNode
    }

    private fun navigateToFile(filePath: String) {
        val virtualFile = LocalFileSystem.getInstance().findFileByPath(filePath) ?: return
        val descriptor = OpenFileDescriptor(project, virtualFile)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }

    override fun dispose() {}
}

/**
 * Custom cell renderer that shows coverage percentage next to package/file names.
 */
private class CoverageTreeCellRenderer : DefaultTreeCellRenderer() {
    override fun getTreeCellRendererComponent(
        tree: JTree,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean,
    ): Component {
        val component = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        val node = (value as? DefaultMutableTreeNode)?.userObject as? CoverageTreeModel.TreeNode
        if (node != null) {
            val pct = node.coveragePercent * 100
            text = "${node.name}  ${"%.1f".format(pct)}%  (${node.coveredStatements}/${node.totalStatements})"

            if (!sel) {
                foreground = when {
                    pct >= 80 -> JBColor(java.awt.Color(0x2E, 0x7D, 0x32), java.awt.Color(0x66, 0xBB, 0x6A))
                    pct >= 50 -> JBColor(java.awt.Color(0xF5, 0x7F, 0x17), java.awt.Color(0xFF, 0xB7, 0x4D))
                    else -> JBColor(java.awt.Color(0xC6, 0x28, 0x28), java.awt.Color(0xEF, 0x53, 0x50))
                }
            }
        }

        return component
    }
}
