package org.openclover.idea.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CloverGlobalConfigTest {

    @Test
    fun defaultValuesAreCorrect() {
        val config = CloverGlobalConfig()
        assertFalse(config.autoScrollToSource)
        assertFalse(config.autoScrollFromSource)
        assertFalse(config.flattenPackages)
    }

    @Test
    fun dataClassCopyWorks() {
        val config = CloverGlobalConfig(autoScrollToSource = true)
        val copy = config.copy(flattenPackages = true)
        assertTrue(copy.autoScrollToSource)
        assertTrue(copy.flattenPackages)
    }
}

class CloverProjectConfigTest {

    @Test
    fun defaultValuesAreCorrect() {
        val config = CloverProjectConfig()
        assertTrue(config.enabled)
        assertTrue(config.buildWithClover)
        assertTrue(config.showCoverage)
        assertTrue(config.showGutter)
        assertTrue(config.showInline)
        assertTrue(config.showTooltips)
        assertTrue(config.showErrorMarks)
        assertTrue(config.showProjectViewAnnotation)
        assertTrue(config.autoRefresh)
        assertEquals(2000L, config.autoRefreshInterval)
        assertEquals("", config.initString)
        assertEquals("0s", config.span)
        assertEquals("", config.contextFilterSpec)
        assertTrue(config.includePassedTestCoverageOnly)
        assertFalse(config.includeFailedTestCoverage)
        assertEquals("UTF-8", config.encoding)
    }

    @Test
    fun modificationWorks() {
        val config = CloverProjectConfig()
        config.enabled = false
        config.initString = "/path/to/clover.db"
        assertFalse(config.enabled)
        assertEquals("/path/to/clover.db", config.initString)
    }
}
