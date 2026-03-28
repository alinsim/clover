package org.openclover.idea.projectview

import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewNodeDecorator
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import org.openclover.idea.CloverProjectService
import org.openclover.idea.coverage.CoverageState

/**
 * Decorates project tree nodes with coverage percentage annotations.
 * Shows coverage % next to Java/Groovy files that have coverage data.
 *
 * Registered in plugin.xml as a projectViewNodeDecorator extension.
 */
class CoverageProjectViewDecorator : ProjectViewNodeDecorator {

    override fun decorate(node: ProjectViewNode<*>, data: PresentationData) {
        val project = node.project ?: return
        val service = CloverProjectService.getInstance(project)
        if (!service.isEnabled || !service.getConfig().showProjectViewAnnotation) return

        val state = service.coverageManager.state.value
        if (state !is CoverageState.Loaded) return

        val virtualFile = getVirtualFile(node) ?: return
        if (!isJavaOrGroovy(virtualFile)) return

        val fileCoverage = state.fileCoverage[virtualFile.path] ?: return
        val pct = fileCoverage.percentCovered

        val color = when {
            pct >= 0.8f -> SimpleTextAttributes.GRAYED_ATTRIBUTES
            pct >= 0.5f -> SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, java.awt.Color(0xCC, 0x88, 0x00))
            else -> SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, java.awt.Color(0xCC, 0x33, 0x33))
        }

        data.addText("  ${"%.0f".format(pct * 100)}%", color)
    }

    private fun getVirtualFile(node: ProjectViewNode<*>): VirtualFile? {
        if (node is PsiFileNode) {
            return node.virtualFile
        }
        return null
    }

    private fun isJavaOrGroovy(file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext == "java" || ext == "groovy" || ext == "kt"
    }
}
