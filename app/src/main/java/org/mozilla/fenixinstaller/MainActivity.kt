package org.mozilla.fenixinstaller

import android.app.ComponentCaller
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import org.mozilla.fenixinstaller.ui.screens.FenixInstallerApp
import org.mozilla.fenixinstaller.ui.theme.FenixInstallerTheme
import java.io.File

class MainActivity : ComponentActivity() {

    private lateinit var viewModel: FenixInstallerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        println("Titouan - ABI: ${Build.SUPPORTED_ABIS.joinToString(",")}")

        viewModel = ViewModelProvider(this)[FenixInstallerViewModel::class.java]
        viewModel.onInstallApk = ::installApk

        enableEdgeToEdge()
        setContent {
            FenixInstallerTheme {
                FenixInstallerApp()
            }
        }
        // Handle the intent that started the activity
        handleDeepLinkIntent(intent)
    }

    override fun onNewIntent(intent: Intent, caller: ComponentCaller) {
        super.onNewIntent(intent, caller)
        // Must set the new intent if the activity is already running
        setIntent(intent)
        // Handle the new intent
        handleDeepLinkIntent(intent)
    }

    private fun handleDeepLinkIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_VIEW) {
            val uri: Uri? = intent.data
            val project = uri?.getQueryParameter("repo") // Extract project/repo
            val revision = uri?.getRevision()
            if (!revision.isNullOrBlank()) {
                Log.d("MainActivity", "Deep link received. Project: ${project ?: "default"}, Revision: $revision")
                viewModel.setRevisionFromDeepLinkAndSearch(project, revision, this) // Pass project and revision
            } else {
                Log.w("MainActivity", "Revision not found in deep link URI: $uri")
            }
        }
    }

    // Consolidated revision extraction, also checking fragment for safety
    private fun Uri.getRevision(): String? {
        var rev = getQueryParameter("revision")
        if (rev.isNullOrBlank() && fragment != null) {
            // Fallback to checking fragment if not in main query params
            // This handles cases like: https://treeherder.mozilla.org/jobs?repo=try&revision=... (standard)
            // And potentially: https://treeherder.mozilla.org/jobs?repo=try#/rev/...
            // Though the primary expected format is repo= & revision= query parameters.
            val fragmentUri = fragment?.toUri()
            rev = fragmentUri?.getQueryParameter("revision")
            // If the revision is part of the fragment path like /rev/revision_hash
            if (rev.isNullOrBlank() && fragmentUri?.pathSegments?.contains("rev") == true) {
                val revIndex = fragmentUri.pathSegments.indexOf("rev")
                if (revIndex != -1 && fragmentUri.pathSegments.size > revIndex + 1) {
                    rev = fragmentUri.pathSegments[revIndex + 1]
                }
            }
        }
        return rev
    }

    private fun installApk(file: File) {
        val authority = "${BuildConfig.APPLICATION_ID}.provider"
        val fileUri: Uri = FileProvider.getUriForFile(this, authority, file)

        Log.d("MainActivity", "Attempting to install APK from URI: $fileUri")

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(fileUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("MainActivity", "Error starting APK install intent", e)
            // Potentially show a toast or a message to the user that installation could not be started
        }
    }
}
