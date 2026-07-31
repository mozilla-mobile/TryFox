package org.mozilla.tryfox.ui.composables

import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.mozilla.tryfox.R
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.install.InstallState
import java.io.File

private const val TAG = "DownloadButton"

@Suppress("LongParameterList", "CyclomaticComplexMethod")
@Composable
fun DownloadButton(
    downloadState: DownloadState,
    onDownloadClick: () -> Unit,
    onInstallClick: (File) -> Unit,
    modifier: Modifier = Modifier,
    inProgressText: String? = null,
    determinateProgressAnimation: DeterminateProgressAnimation = DeterminateProgressAnimation.Rotating,
    installState: InstallState = InstallState.Idle,
    installDisabled: Boolean = false,
    onOpenClick: ((String) -> Unit)? = null,
    debugLabel: String = "action_button",
) {
    val inProgressState = downloadState as? DownloadState.InProgress
    val downloadedState = downloadState as? DownloadState.Downloaded
    val defaultText = stringResource(id = R.string.download_button_downloading)
    val colorScheme = MaterialTheme.colorScheme
    val isInstalling = installState is InstallState.Installing || installState is InstallState.Uninstalling
    val isInstalled = installState is InstallState.Installed
    val isDownloading = inProgressState != null

    LaunchedEffect(downloadState, installState, installDisabled, debugLabel) {
        Log.d(
            TAG,
            "[$debugLabel] state download=${downloadState.javaClass.simpleName} install=${installState.javaClass.simpleName} " +
                "installDisabled=$installDisabled",
        )
    }

    ProgressButton(
        onClick = {
            (installState as? InstallState.Installed)?.let { installed ->
                onOpenClick?.invoke(installed.packageName)
            } ?: downloadedState?.let { onInstallClick(it.file) } ?: onDownloadClick()
        },
        enabled = !installDisabled,
        isLoading = isDownloading || isInstalling,
        progress = if (isInstalling) null else inProgressState
            ?.progress
            ?.takeUnless { inProgressState.isIndeterminate },
        text = if (isInstalled) {
            stringResource(id = R.string.download_button_open)
        } else if (downloadedState == null) {
            stringResource(id = R.string.download_button_download)
        } else {
            stringResource(id = R.string.download_button_install)
        },
        loadingText = if (isInstalling || isInstalled) {
            stringResource(id = R.string.download_button_installing)
        } else {
            inProgressText ?: defaultText
        },
        determinateProgressAnimation = determinateProgressAnimation,
        // Keep the fill stable across every state; the lighter progress ring is
        // deliberately distinct from the primary button background.
        trackColor = colorScheme.onPrimary.copy(alpha = 0.28f),
        indicatorColor = colorScheme.primaryContainer,
        trackEndColor = colorScheme.primaryContainer,
        endingAnimation = EndingAnimation.None,
        containerColor = colorScheme.primary,
        contentColor = colorScheme.onPrimary,
        modifier = modifier,
        semanticsTag = when {
            isInstalled -> "action_button_installed"
            isInstalling -> "action_button_installing"
            downloadedState != null -> "action_button_install_ready"
            inProgressState != null -> "action_button_downloading"
            else -> "action_button_download_initial"
        },
    )
}
