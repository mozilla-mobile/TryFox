package org.mozilla.tryfox.data.managers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.core.net.toUri

/** Interface for managing intents related to installed applications. */
interface IntentManager {
    fun uninstallApk(packageName: String)
}

/** Default implementation of [IntentManager]. */
class DefaultIntentManager(private val context: Context) : IntentManager {
    override fun uninstallApk(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE).apply {
            data = "package:$packageName".toUri()
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No application found to uninstall app", Toast.LENGTH_LONG).show()
            Log.e("MainActivity", "Error uninstalling app", e)
        }
    }
}
