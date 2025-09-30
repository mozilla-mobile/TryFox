package org.mozilla.tryfox.data

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.util.Log
import org.mozilla.tryfox.model.AppState

/**
 * Manages interactions with the Android [PackageManager] to retrieve information
 * about Mozilla applications installed on the device.
 *
 * @property packageManager The Android [PackageManager] instance used to query package information.
 */
class MozillaPackageManager(private val packageManager: PackageManager) {

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
     * @param friendlyName A user-friendly name for the application.
     * @return An [AppState] object representing the application's state.
     */
    private fun getAppState(packageName: String, friendlyName: String): AppState {
        val packageInfo = getPackageInfo(packageName)

        return AppState(
            name = friendlyName,
            packageName = packageName,
            version = packageInfo?.versionName,
            installDateMillis = packageInfo?.lastUpdateTime
        )
    }

    /**
     * The [AppState] for Fenix (Firefox for Android).
     */
    val fenix: AppState by lazy {
        getAppState("org.mozilla.fenix", "Fenix")
    }

    /**
     * The [AppState] for Focus Nightly.
     */
    val focus: AppState by lazy {
        getAppState("org.mozilla.focus.nightly", "Focus Nightly")
    }

    /**
     * The [AppState] for Reference Browser.
     */
    val referenceBrowser: AppState by lazy {
        getAppState("org.mozilla.reference.browser", "Reference Browser")
    }
}
