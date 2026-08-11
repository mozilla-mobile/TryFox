package org.mozilla.tryfox.data

import android.app.Activity
import org.mozilla.tryfox.data.managers.NotificationManager
import org.mozilla.tryfox.data.managers.NotificationPermissionState

class FakeNotificationManager(
    var hasNotificationPermission: Boolean = true,
    var notificationsEnabledValue: Boolean = true,
    var notificationPermissionState: NotificationPermissionState = NotificationPermissionState.GRANTED,
) : NotificationManager {
    var permissionRequestCount: Int = 0
        private set
    var openNotificationSettingsCount: Int = 0
        private set
    private var hasHandledStartupPermissionRequest: Boolean = false

    override fun hasPermission(): Boolean = hasNotificationPermission

    override fun areNotificationsEnabled(): Boolean = notificationsEnabledValue && hasPermission()

    override fun isNotificationPreferenceEnabled(): Boolean = notificationsEnabledValue

    override fun setNotificationsEnabled(enabled: Boolean) {
        notificationsEnabledValue = enabled
    }

    override fun requestPermissionOnFirstAppLaunch(activity: Activity) {
        if (hasHandledStartupPermissionRequest) return
        hasHandledStartupPermissionRequest = true
        requestPermissionIfNeeded(activity)
    }

    override fun requestPermission(activity: Activity) {
        permissionRequestCount++
    }

    override fun permissionState(activity: Activity?): NotificationPermissionState = notificationPermissionState

    override fun openNotificationSettings() {
        openNotificationSettingsCount++
    }
}
