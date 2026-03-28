package org.openclover.idea.editor

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CoverageIconsTest {

    @Test
    fun allIconsLoad() {
        assertNotNull(CoverageIcons.COVERED)
        assertNotNull(CoverageIcons.UNCOVERED)
        assertNotNull(CoverageIcons.PARTIAL)
        assertNotNull(CoverageIcons.CLOVER)
        assertNotNull(CoverageIcons.REFRESH)
        assertNotNull(CoverageIcons.TEST)
        assertNotNull(CoverageIcons.TREEMAP)
        assertNotNull(CoverageIcons.CLOUD)
    }
}
