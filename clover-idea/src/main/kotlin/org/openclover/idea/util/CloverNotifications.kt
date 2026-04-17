package org.openclover.idea.util

import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.project.Project

/**
 * Utility for showing balloon notifications to the user.
 *
 * The notification group "OpenClover" must be registered in plugin.xml:
 * <notificationGroup id="OpenClover" displayType="BALLOON"/>
 */
object CloverNotifications {

    private const val GROUP_ID = "OpenClover"

    /**
     * Show an informational notification.
     */
    fun notifyInfo(project: Project?, message: String) {
        notify(project, message, NotificationType.INFORMATION)
    }

    /**
     * Show a warning notification.
     */
    fun notifyWarning(project: Project?, message: String) {
        notify(project, message, NotificationType.WARNING)
    }

    /**
     * Show an error notification.
     */
    fun notifyError(project: Project?, message: String) {
        notify(project, message, NotificationType.ERROR)
    }

    private fun notify(project: Project?, message: String, type: NotificationType) {
        val group = NotificationGroupManager.getInstance().getNotificationGroup(GROUP_ID)
        val notification = group.createNotification(message, type)
        notification.notify(project)
    }
}
