package org.mozilla.tryfox.data

import kotlinx.coroutines.flow.Flow
import org.mozilla.tryfox.model.AppState

/**
 * Manages interactions with the Android PackageManager to retrieve information
 * about Mozilla applications installed on the device.
 */
interface MozillaPackageManager {
    /**
     * The [AppState] for Fenix (Firefox for Android Nightly).
     */
    val fenix: AppState

    /**
     * The [AppState] for Firefox Release.
     */
    val fenixRelease: AppState

    /**
     * The [AppState] for Firefox Beta.
     */
    val fenixBeta: AppState

    /**
     * The [AppState] for Focus Nightly.
     */
    val focus: AppState

    /**
     * The [AppState] for Reference Browser.
     */
    val referenceBrowser: AppState

    /**
     * The [AppState] for TryFox.
     */
    val tryfox: AppState

    /**
     * A flow that emits an [AppState] whenever a monitored Mozilla application
     * is installed or uninstalled.
     */
    val appStates: Flow<AppState>

    /**
     * Launches the application with the given package name.
     *
     * @param appName The package name of the app to launch.
     */
    fun launchApp(appName: String)
}
