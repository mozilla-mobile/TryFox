package org.mozilla.tryfox.data.managers

import android.app.Activity
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock

class NotificationManagerTest {
    @Test
    fun `requestPermissionIfNeeded requests permission when permission is missing`() {
        val notificationManager = FakeNotificationManager(hasNotificationPermission = false)

        notificationManager.requestPermissionIfNeeded(mock<Activity>())

        assertEquals(1, notificationManager.permissionRequestCount)
    }

    @Test
    fun `requestPermissionIfNeeded does not request permission when it is granted`() {
        val notificationManager = FakeNotificationManager(hasNotificationPermission = true)

        notificationManager.requestPermissionIfNeeded(mock<Activity>())

        assertEquals(0, notificationManager.permissionRequestCount)
    }

    @Test
    fun `disabled notification preference prevents a permission request`() {
        val notificationManager = FakeNotificationManager(hasNotificationPermission = false)
        notificationManager.setNotificationsEnabled(false)

        notificationManager.requestPermissionIfNeeded(mock<Activity>())

        assertFalse(notificationManager.isNotificationPreferenceEnabled())
        assertEquals(0, notificationManager.permissionRequestCount)
    }

    @Test
    fun `notification permission is requested only on the first app launch`() {
        val notificationManager = FakeNotificationManager(hasNotificationPermission = false)
        val activity = mock<Activity>()

        notificationManager.requestPermissionOnFirstAppLaunch(activity)
        notificationManager.requestPermissionOnFirstAppLaunch(activity)

        assertEquals(1, notificationManager.permissionRequestCount)
    }
}
