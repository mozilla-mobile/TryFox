package org.mozilla.tryfox.lan

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class TryFoxLanReceiveNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION_NOTIFICATION_DISMISSED) return

        ContextCompat.startForegroundService(
            context,
            TryFoxLanReceiveService.stopIntent(
                context,
                showStoppedNotification = true,
            ),
        )
    }

    companion object {
        const val ACTION_NOTIFICATION_DISMISSED =
            "org.mozilla.tryfox.lan.action.NOTIFICATION_DISMISSED"
    }
}
