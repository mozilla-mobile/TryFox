package org.mozilla.tryfox.ui.composables

import android.util.Log
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import org.mozilla.tryfox.R
import org.mozilla.tryfox.data.DownloadState
import java.io.File

private const val TAG = "DownloadButton"

@Composable
fun DownloadButton(
    downloadState: DownloadState,
    onDownloadClick: () -> Unit,
    onInstallClick: (File) -> Unit,
    modifier: Modifier = Modifier,
    inProgressText: String? = null,
    determinateProgressAnimation: DeterminateProgressAnimation = DeterminateProgressAnimation.Rotating,
) {
    val inProgressState = downloadState as? DownloadState.InProgress
    val downloadedState = downloadState as? DownloadState.Downloaded
    val defaultText = stringResource(id = R.string.download_button_downloading)
    val colorScheme = MaterialTheme.colorScheme
    val isDownloading = inProgressState != null

    LaunchedEffect(downloadState) {
        Log.d(TAG, "downloadState changed: $downloadState")
    }

    ProgressButton(
        onClick = {
            downloadedState?.let { onInstallClick(it.file) } ?: onDownloadClick()
        },
        enabled = true,
        isLoading = isDownloading,
        progress = inProgressState
            ?.progress
            ?.takeUnless { inProgressState.isIndeterminate },
        text = if (downloadedState == null) {
            stringResource(id = R.string.download_button_download)
        } else {
            stringResource(id = R.string.download_button_install)
        },
        loadingText = inProgressText ?: defaultText,
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
            downloadedState != null -> "action_button_install_ready"
            inProgressState != null -> "action_button_downloading"
            else -> "action_button_download_initial"
        },
    )
}
