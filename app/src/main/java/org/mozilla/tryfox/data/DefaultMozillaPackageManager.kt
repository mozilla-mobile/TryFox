package org.mozilla.tryfox.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.util.FENIX_PACKAGE
import org.mozilla.tryfox.util.FOCUS_PACKAGE
import org.mozilla.tryfox.util.REFERENCE_BROWSER_PACKAGE

class DefaultMozillaPackageManager(private val context: Context) : MozillaPackageManager {

    private val packageManager: PackageManager = context.packageManager

    private fun getPackageInfo(packageName: String): PackageInfo? {
        return try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            Log.d("MozillaPackageManager", "Package not found: $packageName")
            null
        } catch (e: Exception) {
            Log.e("MozillaPackageManager", "Error getting package info for $packageName", e)
            null
        }
    }

    private fun getAppState(packageName: String): AppState {
        val packageInfo = getPackageInfo(packageName)

        return AppState(
            name = apps[packageName] ?: "",
            packageName = packageName,
            version = packageInfo?.versionName,
            installDateMillis = packageInfo?.lastUpdateTime,
        )
    }

    private val apps = mapOf(
        FENIX_PACKAGE to "Fenix",
        FOCUS_PACKAGE to "Focus",
        REFERENCE_BROWSER_PACKAGE to "Reference Browser",
    )

    override val fenix: AppState
        get() = getAppState("org.mozilla.fenix")

    override val focus: AppState
        get() = getAppState("org.mozilla.focus.nightly")

    override val referenceBrowser: AppState
        get() = getAppState("org.mozilla.reference.browser")

    override val appStates: Flow<AppState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action == Intent.ACTION_PACKAGE_ADDED || intent.action == Intent.ACTION_PACKAGE_REMOVED) {
                    val packageName = intent.data?.schemeSpecificPart
                    if (packageName != null && packageName in apps.keys) {
                        trySend(getAppState(packageName))
                    }
                }
            }
        }

        val intentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addDataScheme("package")
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            intentFilter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    override fun launchApp(appName: String) {
        val intent = packageManager.getLaunchIntentForPackage(appName)
        intent?.let(context::startActivity)
    }
}
