package org.openclover.idea.editor

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.editor.markup.GutterIconRenderer
import org.openclover.idea.coverage.FileCoverageInfo
import org.openclover.idea.coverage.LineCoverageDetail
import org.openclover.idea.coverage.LineCoverageStatus
import javax.swing.Icon

/**
 * Renders a coverage status icon in the editor gutter for a single line.
 * Shows green (covered), red (uncovered), or yellow (partial branch) icons.
 * Tooltip shows detailed hit counts and branch information.
 */
class CoverageGutterRenderer(
    private val status: LineCoverageStatus,
    private val fileCoverage: FileCoverageInfo,
    private val line: Int,
) : GutterIconRenderer() {

    override fun getIcon(): Icon = when (status) {
        LineCoverageStatus.COVERED -> CoverageIcons.COVERED
        LineCoverageStatus.UNCOVERED -> CoverageIcons.UNCOVERED
        LineCoverageStatus.PARTIAL -> CoverageIcons.PARTIAL
    }

    override fun getTooltipText(): String {
        val detail = fileCoverage.lineDetails[line]
        return detail?.toTooltip(line) ?: defaultTooltip()
    }

    private fun defaultTooltip(): String = when (status) {
        LineCoverageStatus.COVERED -> "Line $line: covered"
        LineCoverageStatus.UNCOVERED -> "Line $line: not covered"
        LineCoverageStatus.PARTIAL -> "Line $line: partially covered (branch)"
    }

    override fun getClickAction(): AnAction? = null

    override fun isNavigateAction(): Boolean = false

    override fun getAlignment(): Alignment = Alignment.LEFT

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is CoverageGutterRenderer) return false
        return status == other.status && line == other.line
    }

    override fun hashCode(): Int = 31 * status.hashCode() + line
}
