package org.mozilla.tryfox.data.managers

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

enum class NotificationPermissionState {
    GRANTED,
    REQUESTABLE,
    BLOCKED,
}

/** Manages the runtime permission required to post notifications. */
interface NotificationManager {
    /** Returns whether the application can post notifications. */
    fun hasPermission(): Boolean

    /** Returns whether notifications are enabled in TryFox and permitted by Android. */
    fun areNotificationsEnabled(): Boolean

    /** Returns whether notifications are enabled in TryFox's own preference. */
    fun isNotificationPreferenceEnabled(): Boolean

    /** Updates TryFox's notification preference. */
    fun setNotificationsEnabled(enabled: Boolean)

    /** Handles the one-time notification permission request made when the app first launches. */
    fun requestPermissionOnFirstAppLaunch(activity: Activity)

    /** Requests notification permission when the platform requires it. */
    fun requestPermission(activity: Activity)

    /** Returns whether Android can still show the notification permission prompt. */
    fun permissionState(activity: Activity?): NotificationPermissionState

    /** Opens the application's notification settings. */
    fun openNotificationSettings()

    /** Requests notification permission only when it has not already been granted. */
    fun requestPermissionIfNeeded(activity: Activity) {
        if (isNotificationPreferenceEnabled() && !hasPermission()) {
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

    override fun areNotificationsEnabled(): Boolean =
        isNotificationPreferenceEnabled() && hasPermission()

    override fun isNotificationPreferenceEnabled(): Boolean =
        preferences.getBoolean(NOTIFICATIONS_ENABLED, true)

    override fun setNotificationsEnabled(enabled: Boolean) {
        preferences.edit().putBoolean(NOTIFICATIONS_ENABLED, enabled).apply()
    }

    override fun requestPermissionOnFirstAppLaunch(activity: Activity) {
        if (preferences.getBoolean(HAS_HANDLED_STARTUP_PERMISSION_REQUEST, false)) return
        preferences.edit().putBoolean(HAS_HANDLED_STARTUP_PERMISSION_REQUEST, true).apply()
        requestPermissionIfNeeded(activity)
    }

    override fun requestPermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            preferences.edit().putBoolean(HAS_REQUESTED_NOTIFICATION_PERMISSION, true).apply()
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                NOTIFICATION_PERMISSION_REQUEST_CODE,
            )
        }
    }

    override fun permissionState(activity: Activity?): NotificationPermissionState {
        if (hasPermission()) return NotificationPermissionState.GRANTED
        if (!preferences.getBoolean(HAS_REQUESTED_NOTIFICATION_PERMISSION, false)) {
            return NotificationPermissionState.REQUESTABLE
        }
        return if (
            activity != null &&
            ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)
        ) {
            NotificationPermissionState.REQUESTABLE
        } else {
            NotificationPermissionState.BLOCKED
        }
    }

    override fun openNotificationSettings() {
        context.startActivity(
            android.content.Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
            },
        )
    }

    private companion object {
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 1001
        private const val NOTIFICATION_PREFERENCES = "notification_preferences"
        private const val HAS_REQUESTED_NOTIFICATION_PERMISSION = "has_requested_notification_permission"
        private const val HAS_HANDLED_STARTUP_PERMISSION_REQUEST = "has_handled_startup_permission_request"
        private const val NOTIFICATIONS_ENABLED = "notifications_enabled"
    }

    private val preferences by lazy {
        context.getSharedPreferences(NOTIFICATION_PREFERENCES, Context.MODE_PRIVATE)
    }
}
