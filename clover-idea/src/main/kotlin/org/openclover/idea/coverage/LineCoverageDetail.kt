package org.openclover.idea.coverage

/**
 * Detailed coverage information for a single source line.
 * Used to generate rich tooltips in the editor gutter.
 */
data class LineCoverageDetail(
    val status: LineCoverageStatus,
    val statementHits: Int = 0,
    val branchTrueHits: Int = -1,
    val branchFalseHits: Int = -1,
    val methodHits: Int = -1,
) {
    private val hasBranch: Boolean get() = branchTrueHits >= 0 || branchFalseHits >= 0
    private val hasMethod: Boolean get() = methodHits >= 0

    /**
     * Generate a human-readable tooltip for the gutter icon.
     */
    fun toTooltip(line: Int): String {
        val parts = mutableListOf<String>()

        parts.add("Line $line")

        when (status) {
            LineCoverageStatus.COVERED -> {
                if (hasMethod) {
                    parts.add("method entered $methodHits time${plural(methodHits)}")
                }
                if (statementHits > 0) {
                    parts.add("executed $statementHits time${plural(statementHits)}")
                }
                if (hasBranch) {
                    parts.add("true branch: $branchTrueHits, false branch: $branchFalseHits")
                }
            }
            LineCoverageStatus.UNCOVERED -> {
                parts.add("not covered")
            }
            LineCoverageStatus.PARTIAL -> {
                parts.add("partially covered")
                if (hasBranch) {
                    parts.add("true branch: $branchTrueHits, false branch: $branchFalseHits")
                }
                if (statementHits > 0) {
                    parts.add("executed $statementHits time${plural(statementHits)}")
                }
            }
        }

        return parts.joinToString(" — ")
    }

    private fun plural(count: Int): String = if (count == 1) "" else "s"
}
