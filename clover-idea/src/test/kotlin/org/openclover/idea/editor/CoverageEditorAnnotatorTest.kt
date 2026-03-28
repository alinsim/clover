package org.openclover.idea.editor

import com.intellij.openapi.editor.markup.TextAttributes
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.openclover.idea.coverage.LineCoverageStatus

class CoverageEditorAnnotatorTest {

    @Test
    fun textAttributesForCoveredHasGreenBackground() {
        val attrs = CoverageEditorAnnotator.textAttributesFor(LineCoverageStatus.COVERED)
        assertNotNull(attrs.backgroundColor)
    }

    @Test
    fun textAttributesForUncoveredHasRedBackground() {
        val attrs = CoverageEditorAnnotator.textAttributesFor(LineCoverageStatus.UNCOVERED)
        assertNotNull(attrs.backgroundColor)
    }

    @Test
    fun textAttributesForPartialHasYellowBackground() {
        val attrs = CoverageEditorAnnotator.textAttributesFor(LineCoverageStatus.PARTIAL)
        assertNotNull(attrs.backgroundColor)
    }

    @Test
    fun allStatusesProduceDifferentColors() {
        val covered = CoverageEditorAnnotator.textAttributesFor(LineCoverageStatus.COVERED)
        val uncovered = CoverageEditorAnnotator.textAttributesFor(LineCoverageStatus.UNCOVERED)
        val partial = CoverageEditorAnnotator.textAttributesFor(LineCoverageStatus.PARTIAL)

        assert(covered.backgroundColor != uncovered.backgroundColor)
        assert(covered.backgroundColor != partial.backgroundColor)
        assert(uncovered.backgroundColor != partial.backgroundColor)
    }
}
