package org.openclover.idea.config

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CloverProjectConfigEncodingTest {

    @Test
    fun encodingDefaultsToUtf8() {
        val config = CloverProjectConfig()
        assertEquals("UTF-8", config.encoding)
    }

    @Test
    fun encodingCanBeModified() {
        val config = CloverProjectConfig()
        config.encoding = "ISO-8859-1"
        assertEquals("ISO-8859-1", config.encoding)
    }
}
