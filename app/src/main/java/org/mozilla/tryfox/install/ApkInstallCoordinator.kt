package org.mozilla.tryfox.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Owns PackageInstaller sessions started from unified search. */
@Suppress("NestedBlockDepth", "TooManyFunctions")
class ApkInstallCoordinator(private val context: Context) {
    private data class Operation(val artifactKey: String, val file: File, val packageName: String)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val packageInstaller = context.packageManager.packageInstaller
    private val requestCodes = AtomicInteger(10_000)
    private val operations = mutableMapOf<String, Operation>()
    private var activeOperationId: String? = null

    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val states: StateFlow<Map<String, InstallState>> = _states.asStateFlow()
    private val _uninstallRequests = MutableSharedFlow<UninstallRequest>(extraBufferCapacity = 1)
    val uninstallRequests: SharedFlow<UninstallRequest> = _uninstallRequests.asSharedFlow()
    private val _successfulInstalls = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val successfulInstalls: SharedFlow<String> = _successfulInstalls.asSharedFlow()

    fun install(artifactKey: String, file: File) {
        if (activeOperationId != null) return
        activeOperationId = artifactKey
        _states.value = _states.value + (artifactKey to InstallState.Installing)
        scope.launch { prepareAndInstall(artifactKey, file) }
    }

    fun cancelConflict(artifactKey: String) {
        if (activeOperationId == artifactKey) activeOperationId = null
        operations.remove(artifactKey)
        _states.value = _states.value + (artifactKey to InstallState.Idle)
    }

    fun confirmUninstallAndRetry(artifactKey: String) {
        val operation = operations[artifactKey] ?: return
        _states.value = _states.value + (artifactKey to InstallState.Uninstalling)
        _uninstallRequests.tryEmit(UninstallRequest(artifactKey, operation.packageName))
    }

    fun onUninstallResult(artifactKey: String, succeeded: Boolean) {
        val operation = operations[artifactKey] ?: return
        if (!succeeded || isInstalled(operation.packageName)) {
            logcat(LogPriority.WARN, TAG) {
                "Uninstall failed artifactKey=$artifactKey package=${operation.packageName} " +
                    "activitySucceeded=$succeeded packageStillInstalled=${isInstalled(operation.packageName)}"
            }
            fail(artifactKey, "Uninstall was canceled or did not complete.")
            return
        }
        _states.value = _states.value + (artifactKey to InstallState.Installing)
        scope.launch { commit(operation) }
    }

    fun openInstalledApp(packageName: String) {
        val launchIntent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (launchIntent == null) {
            logcat(LogPriority.WARN, TAG) { "No launch activity for $packageName" }
            return
        }
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }

    fun onInstallResult(intent: Intent) {
        val artifactKey = intent.getStringExtra(EXTRA_ARTIFACT_KEY) ?: return
        val operation = operations[artifactKey] ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                @Suppress("DEPRECATION")
                val confirmationIntent = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
                if (confirmationIntent == null) {
                    fail(artifactKey, "Android did not provide an installation confirmation screen.")
                } else {
                    confirmationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context.startActivity(confirmationIntent)
                }
            }

            PackageInstaller.STATUS_SUCCESS -> succeed(artifactKey)
            PackageInstaller.STATUS_FAILURE_CONFLICT -> {
                if (statusMessage.contains(SHARED_USER_SIGNATURE_FAILURE)) {
                    logcat(LogPriority.WARN, TAG) {
                        "Shared-user signature conflict artifactKey=$artifactKey package=${operation.packageName} " +
                            "message=$statusMessage"
                    }
                    fail(artifactKey, SHARED_USER_SIGNATURE_USER_MESSAGE)
                    return
                }
                val conflictingPackage = intent.getStringExtra(PackageInstaller.EXTRA_OTHER_PACKAGE_NAME) ?: operation.packageName
                logcat(LogPriority.WARN, TAG) {
                    "Install conflict artifactKey=$artifactKey package=${operation.packageName} " +
                        "conflictingPackage=$conflictingPackage message=$statusMessage"
                }
                conflict(artifactKey, conflictingPackage)
            }
            else -> {
                if (statusMessage.contains("VERSION_DOWNGRADE", ignoreCase = true) && isInstalled(operation.packageName)) {
                    conflict(artifactKey, operation.packageName)
                } else {
                    logcat(LogPriority.WARN, TAG) { "Install failed status=$status message=$statusMessage" }
                    fail(artifactKey, userMessage(status))
                }
            }
        }
    }

    private fun prepareAndInstall(artifactKey: String, file: File) {
        if (!file.isFile) {
            fail(artifactKey, "The downloaded APK is no longer available.")
            return
        }
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
        val packageName = archive?.packageName
        if (packageName == null) {
            fail(artifactKey, "The downloaded file is not a valid APK.")
            return
        }
        val operation = Operation(artifactKey, file, packageName)
        operations[artifactKey] = operation
        val incomingVersion = archive.let(PackageInfoCompat::getLongVersionCode)
        val installedVersion = installedVersion(packageName)
        if (installedVersion != null && installedVersion > incomingVersion) {
            conflict(artifactKey, packageName)
            return
        }
        commit(operation)
    }

    private fun commit(operation: Operation) {
        var sessionId: Int? = null
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(operation.packageName)
                setSize(operation.file.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            sessionId = packageInstaller.createSession(params)
            packageInstaller.openSession(sessionId).use { session ->
                operation.file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, operation.file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(statusReceiver(operation.artifactKey))
            }
        } catch (e: Exception) {
            sessionId?.let(packageInstaller::abandonSession)
            logcat(LogPriority.ERROR, TAG) { "Could not create install session: ${e.message}" }
            fail(operation.artifactKey, "Could not start installation.")
        }
    }

    private fun statusReceiver(artifactKey: String) = PendingIntent.getBroadcast(
        context,
        requestCodes.incrementAndGet(),
        Intent(context, InstallResultReceiver::class.java).putExtra(EXTRA_ARTIFACT_KEY, artifactKey),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    ).intentSender

    private fun conflict(artifactKey: String, packageName: String) {
        _states.value = _states.value + (artifactKey to InstallState.Conflict(packageName))
    }

    private fun succeed(artifactKey: String) {
        val packageName = operations[artifactKey]?.packageName ?: return
        activeOperationId = null
        operations.remove(artifactKey)
        _states.value = _states.value + (artifactKey to InstallState.Installed(packageName))
        _successfulInstalls.tryEmit(artifactKey)
    }

    private fun fail(artifactKey: String, message: String) {
        val packageName = operations[artifactKey]?.packageName
        logcat(LogPriority.ERROR, TAG) {
            "Install failed artifactKey=$artifactKey package=$packageName message=$message"
        }
        activeOperationId = null
        operations.remove(artifactKey)
        _states.value = _states.value + (artifactKey to InstallState.Failed(message))
    }

    private fun installedVersion(packageName: String): Long? = try {
        PackageInfoCompat.getLongVersionCode(context.packageManager.getPackageInfo(packageName, 0))
    } catch (_: PackageManager.NameNotFoundException) {
        null
    }

    private fun isInstalled(packageName: String) = installedVersion(packageName) != null

    private fun userMessage(status: Int) = when (status) {
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "This APK is not compatible with this device."
        PackageInstaller.STATUS_FAILURE_INVALID -> "Android rejected this APK as invalid."
        PackageInstaller.STATUS_FAILURE_STORAGE -> "There is not enough storage to install this APK."
        PackageInstaller.STATUS_FAILURE_BLOCKED -> "Android blocked this installation."
        PackageInstaller.STATUS_FAILURE_ABORTED -> "Installation was canceled."
        PackageInstaller.STATUS_FAILURE_TIMEOUT -> "Installation timed out."
        else -> "Android could not install this APK."
    }

    private companion object {
        const val TAG = "ApkInstallCoordinator"
        const val EXTRA_ARTIFACT_KEY = "org.mozilla.tryfox.install.ARTIFACT_KEY"
        const val SHARED_USER_SIGNATURE_FAILURE = "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE"
        const val SHARED_USER_SIGNATURE_USER_MESSAGE =
            "This build is signed differently from an installed Firefox app. Android cannot install them together. " +
                "Uninstall the conflicting Firefox app and its local data, then try again."
    }
}
