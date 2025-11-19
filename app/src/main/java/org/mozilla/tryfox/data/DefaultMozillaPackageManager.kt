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
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_NIGHTLY_PACKAGE
import org.mozilla.tryfox.util.FENIX_RELEASE_PACKAGE
import org.mozilla.tryfox.util.FENIX_BETA_PACKAGE
import org.mozilla.tryfox.util.FENIX_NIGHTLY
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_NIGHTLY_PACKAGE
import org.mozilla.tryfox.util.REFERENCE_BROWSER
import org.mozilla.tryfox.util.REFERENCE_BROWSER_PACKAGE
import org.mozilla.tryfox.util.TRYFOX
import org.mozilla.tryfox.util.TRYFOX_PACKAGE

class DefaultMozillaPackageManager(private val context: Context) : MozillaPackageManager {

    private val packageManager: PackageManager = context.packageManager
    private val TAG = "MozillaPackageManager"

    private fun getPackageInfo(packageName: String): PackageInfo? {
        return try {
            packageManager.getPackageInfo(packageName, 0)
        } catch (_: PackageManager.NameNotFoundException) {
            Log.d(TAG, "Package not found: $packageName")
            null
        } catch (e: Exception) {
            Log.e(TAG, "Error getting package info for $packageName", e)
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
        FENIX_NIGHTLY_PACKAGE to FENIX_NIGHTLY,
        FENIX_RELEASE_PACKAGE to FENIX_RELEASE,
        FENIX_BETA_PACKAGE to FENIX_BETA,
        FOCUS_NIGHTLY_PACKAGE to FOCUS,
        REFERENCE_BROWSER_PACKAGE to REFERENCE_BROWSER,
        TRYFOX_PACKAGE to TRYFOX,
    )

    override val fenix: AppState
        get() = getAppState(FENIX_NIGHTLY_PACKAGE)

    override val fenixRelease: AppState
        get() = getAppState(FENIX_RELEASE_PACKAGE)

    override val fenixBeta: AppState
        get() = getAppState(FENIX_BETA_PACKAGE)

    override val focus: AppState
        get() = getAppState(FOCUS_NIGHTLY_PACKAGE)

    override val referenceBrowser: AppState
        get() = getAppState(REFERENCE_BROWSER_PACKAGE)

    override val tryfox: AppState
        get() = getAppState(TRYFOX_PACKAGE)

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
