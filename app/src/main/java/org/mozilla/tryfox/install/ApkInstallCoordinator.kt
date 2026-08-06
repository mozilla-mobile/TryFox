package org.mozilla.tryfox.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
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
import org.mozilla.tryfox.data.InstalledTryBuild
import org.mozilla.tryfox.data.repositories.InstalledTryBuildRepository
import org.mozilla.tryfox.util.FENIX_DEBUG_PACKAGE
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/** Owns every APK installation session started by TryFox. */
@Suppress("NestedBlockDepth", "TooManyFunctions")
class ApkInstallCoordinator(
    private val context: Context,
    private val installedTryBuildRepository: InstalledTryBuildRepository,
) {
    private data class Operation(
        val artifactKey: String,
        val file: File,
        val packageName: String,
        val versionName: String?,
        val versionCode: Long,
        val provenance: TryBuildProvenance?,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sessionFactory = PackageInstallerSessionFactory(context.packageManager.packageInstaller)
    private val requestCodes = AtomicInteger(10_000)
    private val operations = mutableMapOf<String, Operation>()
    private var activeOperationId: String? = null

    private val _states = MutableStateFlow<Map<String, InstallState>>(emptyMap())
    val states: StateFlow<Map<String, InstallState>> = _states.asStateFlow()
    private val _uninstallRequests = MutableSharedFlow<UninstallRequest>(extraBufferCapacity = 1)
    val uninstallRequests: SharedFlow<UninstallRequest> = _uninstallRequests.asSharedFlow()
    private val _successfulInstalls = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val successfulInstalls: SharedFlow<String> = _successfulInstalls.asSharedFlow()

    fun install(artifactKey: String, file: File, provenance: TryBuildProvenance? = null) {
        if (activeOperationId != null) return
        activeOperationId = artifactKey
        _states.value = _states.value + (artifactKey to InstallState.Installing)
        scope.launch { prepareAndInstall(artifactKey, file, provenance) }
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

    suspend fun onInstallResult(intent: Intent) {
        val artifactKey = intent.getStringExtra(EXTRA_ARTIFACT_KEY) ?: return
        val inMemoryOperation = operations[artifactKey]
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val statusMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()
        if (status == PackageInstaller.STATUS_SUCCESS) {
            val operation = inMemoryOperation ?: intent.toResultOperation(artifactKey) ?: return
            succeed(artifactKey, operation)
            return
        }
        val operation = inMemoryOperation ?: return
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

    private fun prepareAndInstall(artifactKey: String, file: File, provenance: TryBuildProvenance?) {
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
        val incomingVersion = archive.let(PackageInfoCompat::getLongVersionCode)
        val operation = Operation(artifactKey, file, packageName, archive.versionName, incomingVersion, provenance)
        operations[artifactKey] = operation
        val installedVersion = installedVersion(packageName)
        if (installedVersion != null && installedVersion > incomingVersion) {
            conflict(artifactKey, packageName)
            return
        }
        commit(operation)
    }

    private fun commit(operation: Operation) {
        try {
            sessionFactory.commit(operation.file, operation.packageName, statusReceiver(operation))
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, TAG) { "Could not create install session: ${e.message}" }
            fail(operation.artifactKey, "Could not start installation.")
        }
    }

    private fun statusReceiver(operation: Operation) = PendingIntent.getBroadcast(
        context,
        requestCodes.incrementAndGet(),
        Intent(context, InstallResultReceiver::class.java)
            .setData(
                Uri.Builder()
                    .scheme("tryfox")
                    .authority("install-result")
                    .appendPath(operation.artifactKey)
                    .build(),
            )
            .putExtra(EXTRA_ARTIFACT_KEY, operation.artifactKey)
            .putExtra(EXTRA_PACKAGE_NAME, operation.packageName)
            .putExtra(EXTRA_VERSION_NAME, operation.versionName)
            .putExtra(EXTRA_VERSION_CODE, operation.versionCode)
            .putExtra(EXTRA_PROJECT, operation.provenance?.project)
            .putExtra(EXTRA_REVISION, operation.provenance?.revision)
            .putExtra(EXTRA_COMMIT_MESSAGE, operation.provenance?.commitMessage),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
    ).intentSender

    private fun conflict(artifactKey: String, packageName: String) {
        _states.value = _states.value + (artifactKey to InstallState.Conflict(packageName))
    }

    private suspend fun succeed(artifactKey: String, operation: Operation) {
        val packageName = operation.packageName
        operation.provenance
            ?.takeIf { packageName == FENIX_DEBUG_PACKAGE }
            ?.let { provenance ->
                try {
                    installedTryBuildRepository.save(
                        InstalledTryBuild(
                            packageName = packageName,
                            project = provenance.project,
                            revision = provenance.revision,
                            commitMessage = provenance.commitMessage,
                            versionName = operation.versionName,
                            versionCode = operation.versionCode,
                        ),
                    )
                } catch (exception: Exception) {
                    logcat(LogPriority.ERROR, TAG) {
                        "Could not persist Try build provenance for package=$packageName: ${exception.message}"
                    }
                }
            }
        activeOperationId = null
        operations.remove(artifactKey)
        _states.value = _states.value + (artifactKey to InstallState.Installed(packageName))
        _successfulInstalls.tryEmit(artifactKey)
    }

    private fun Intent.toResultOperation(artifactKey: String): Operation? {
        val packageName = getStringExtra(EXTRA_PACKAGE_NAME)?.takeIf(String::isNotBlank) ?: return null
        val versionCode = getLongExtra(EXTRA_VERSION_CODE, UNKNOWN_VERSION_CODE)
        if (versionCode == UNKNOWN_VERSION_CODE) return null
        val project = getStringExtra(EXTRA_PROJECT)
        val revision = getStringExtra(EXTRA_REVISION)
        val commitMessage = getStringExtra(EXTRA_COMMIT_MESSAGE)
        val provenance = if (project != null && revision != null && commitMessage != null) {
            TryBuildProvenance(project, revision, commitMessage)
        } else {
            null
        }
        return Operation(
            artifactKey = artifactKey,
            file = File(""),
            packageName = packageName,
            versionName = getStringExtra(EXTRA_VERSION_NAME),
            versionCode = versionCode,
            provenance = provenance,
        )
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
        const val EXTRA_PACKAGE_NAME = "org.mozilla.tryfox.install.PACKAGE_NAME"
        const val EXTRA_VERSION_NAME = "org.mozilla.tryfox.install.VERSION_NAME"
        const val EXTRA_VERSION_CODE = "org.mozilla.tryfox.install.VERSION_CODE"
        const val EXTRA_PROJECT = "org.mozilla.tryfox.install.PROJECT"
        const val EXTRA_REVISION = "org.mozilla.tryfox.install.REVISION"
        const val EXTRA_COMMIT_MESSAGE = "org.mozilla.tryfox.install.COMMIT_MESSAGE"
        const val UNKNOWN_VERSION_CODE = Long.MIN_VALUE
        const val SHARED_USER_SIGNATURE_FAILURE = "INSTALL_FAILED_SHARED_USER_INCOMPATIBLE"
        const val SHARED_USER_SIGNATURE_USER_MESSAGE =
            "This build is signed differently from an installed Firefox app. Android cannot install them together. " +
                "Uninstall the conflicting Firefox app and its local data, then try again."
    }
}

internal class PackageInstallerSessionFactory(
    private val packageInstaller: PackageInstaller,
) {
    fun commit(file: File, packageName: String, statusReceiver: IntentSender) {
        var sessionId: Int? = null
        try {
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(packageName)
                setSize(file.length())
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    setPackageSource(PackageInstaller.PACKAGE_SOURCE_DOWNLOADED_FILE)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }
            sessionId = packageInstaller.createSession(params)
            packageInstaller.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(statusReceiver)
            }
        } catch (exception: Exception) {
            sessionId?.let(packageInstaller::abandonSession)
            throw exception
        }
    }
}
