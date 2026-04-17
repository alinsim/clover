package org.openclover.idea.statusbar

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for CoverageStatusBarWidgetFactory.
 */
class CoverageStatusBarWidgetFactoryTest {

    @Test
    fun factoryIdIsStable() {
        val factory = CoverageStatusBarWidgetFactory()
        assertEquals(CoverageStatusBarWidgetFactory.WIDGET_ID, factory.id)
    }

    @Test
    fun factoryHasDisplayName() {
        val factory = CoverageStatusBarWidgetFactory()
        assertNotNull(factory.displayName)
        assertTrue(factory.displayName.isNotBlank())
    }

    @Test
    fun widgetIdMatchesFactoryId() {
        assertEquals("OpenCloverCoverage", CoverageStatusBarWidgetFactory.WIDGET_ID)
    }
}
