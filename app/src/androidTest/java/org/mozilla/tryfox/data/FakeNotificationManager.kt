package org.mozilla.tryfox.data

import android.app.Activity
import org.mozilla.tryfox.data.managers.NotificationManager

class FakeNotificationManager(
    var hasNotificationPermission: Boolean = true,
) : NotificationManager {
    var permissionRequestCount: Int = 0
        private set

    override fun hasPermission(): Boolean = hasNotificationPermission

    override fun requestPermission(activity: Activity) {
        permissionRequestCount++
    }
}
