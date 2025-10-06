package org.mozilla.tryfox.data

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import org.mozilla.tryfox.model.AppState
import org.mozilla.tryfox.util.FENIX_PACKAGE
import org.mozilla.tryfox.util.FOCUS_PACKAGE
import org.mozilla.tryfox.util.REFERENCE_BROWSER_PACKAGE

/**
 * Manages interactions with the Android [PackageManager] to retrieve information
 * about Mozilla applications installed on the device.
 *
 * @property context The application context.
 */
class MozillaPackageManager(private val context: Context) {

    private val packageManager: PackageManager = context.packageManager

    /**
     * Retrieves [PackageInfo] for a given package name.
     *
     * @param packageName The name of the package to retrieve information for.
     * @return [PackageInfo] if the package is found and no error occurs, null otherwise.
     */
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

    /**
     * Constructs an [AppState] object for a given package name and friendly name.
     *
     * @param packageName The package name of the application.
     * @return An [AppState] object representing the application's state.
     */
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

    /**
     * The [AppState] for Fenix (Firefox for Android).
     */
    fun fenix(): AppState = getAppState("org.mozilla.fenix")

    /**
     * The [AppState] for Focus Nightly.
     */
    fun focus(): AppState = getAppState("org.mozilla.focus.nightly")

    /**
     * The [AppState] for Reference Browser.
     */
    fun referenceBrowser(): AppState = getAppState("org.mozilla.reference.browser")

    val appStates: Flow<AppState> = callbackFlow {
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

        context.registerReceiver(receiver, intentFilter)

        awaitClose {
            context.unregisterReceiver(receiver)
        }
    }

    fun launchApp(appName: String) {
        val intent = packageManager.getLaunchIntentForPackage(appName)
        intent?.let(context::startActivity)
    }
}
