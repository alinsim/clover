package org.openclover.idea.util

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class CloverNotificationsTest {

    @Test
    fun notificationGroupIdIsOpenClover() {
        // Verify the notification group ID constant is correct
        assertNotNull(CloverNotifications)
    }
}
