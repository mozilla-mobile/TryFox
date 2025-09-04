package org.mozilla.fenixinstaller.ui.composables

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag // Added import
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mozilla.fenixinstaller.R
import org.mozilla.fenixinstaller.data.DownloadState
import java.io.File

@Composable
fun DownloadButton(
    downloadState: DownloadState,
    onDownloadClick: () -> Unit,
    onInstallClick: (File) -> Unit,
) {
    when (downloadState) {
        is DownloadState.Downloaded -> {
            Button(
                onClick = { onInstallClick(downloadState.file) },
                modifier = Modifier.testTag("action_button_install_ready") // Tag for Install state
            ) {
                Text(stringResource(id = R.string.download_button_install))
            }
        }
        is DownloadState.InProgress -> {
            Button(
                onClick = {}, // Corrected: was missing comma
                enabled = false,
                modifier = Modifier.testTag("action_button_downloading") // Tag for Downloading state
            ) {
                if (downloadState.progress > 0f) {
                    CircularProgressIndicator(
                        progress = { downloadState.progress },
                        modifier = Modifier
                            .size(ButtonDefaults.IconSize)
                            .testTag("progress_indicator_determinate"), // Tag for determinate progress
                        strokeWidth = 2.dp
                    )
                } else {
                    CircularProgressIndicator(
                        modifier = Modifier
                            .size(ButtonDefaults.IconSize)
                            .testTag("progress_indicator_indeterminate"), // Tag for indeterminate progress
                        strokeWidth = 2.dp
                    )
                }
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(stringResource(id = R.string.download_button_downloading))
            }
        }
        is DownloadState.NotDownloaded, is DownloadState.DownloadFailed -> {
            Button(
                onClick = onDownloadClick,
                modifier = Modifier.testTag("action_button_download_initial") // Tag for Download state
            ) {
                Text(stringResource(id = R.string.download_button_download))
            }
        }
    }
}
