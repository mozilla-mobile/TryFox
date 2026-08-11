package org.mozilla.tryfox.data.managers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/** Manages the runtime permission required to post notifications. */
interface NotificationManager {
    /** Returns whether the application can post notifications. */
    fun hasPermission(): Boolean

    /** Requests notification permission when the platform requires it. */
    fun requestPermission(activity: Activity)

    /** Requests notification permission only when it has not already been granted. */
    fun requestPermissionIfNeeded(activity: Activity) {
        if (!hasPermission()) {
            requestPermission(activity)
        }
    }
}

/** Android implementation of [NotificationManager]. */
class DefaultNotificationManager(private val context: Context) : NotificationManager {
    override fun hasPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    override fun requestPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE,
            )
        }
    }

    private companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
    }
}
