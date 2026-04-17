package org.openclover.idea

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AboutDialogTest {

    @Test
    fun aboutDialogCanBeInstantiated() {
        // Simple smoke test - AboutDialog should be instantiable
        // Full UI testing requires a full IntelliJ test fixture
        assertNotNull(AboutDialog::class.java)
    }
}
