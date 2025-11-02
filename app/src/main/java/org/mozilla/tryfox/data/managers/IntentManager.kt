package org.mozilla.tryfox.data.managers

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.core.content.FileProvider
import org.mozilla.tryfox.BuildConfig
import java.io.File

/**
 * Interface for managing intents related to APK installation.
 */
interface IntentManager {
    /**
     * Initiates the installation of an APK file.
     *
     * @param file The APK file to install.
     */
    fun installApk(file: File)
}

/**
 * Default implementation of [IntentManager] that handles APK installation using a [FileProvider].
 *
 * @param context The application context.
 */
class DefaultIntentManager(private val context: Context) : IntentManager {
    /**
     * Creates an intent to install an APK file and starts the corresponding activity.
     * If no application is found to handle the intent, a toast message is displayed.
     *
     * @param file The APK file to install.
     */
    override fun installApk(file: File) {
        val fileUri: Uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.provider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No application found to install APK", Toast.LENGTH_LONG).show()
            Log.e("IntentManager", "Error installing APK", e)
        }
    }
}
