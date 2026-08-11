package org.mozilla.tryfox.ui.composables

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.R
import org.mozilla.tryfox.install.InstallState
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.ApksResult
import org.mozilla.tryfox.ui.models.AppUiModel

@Composable
fun TryFoxCard(
    modifier: Modifier = Modifier,
    app: AppUiModel,
    onDownloadClick: (ApkUiModel) -> Unit,
    onInstallClick: (ApkUiModel) -> Unit,
    installStates: Map<String, InstallState>,
    onOpenInstalledApp: (String) -> Unit,
) {
    val latestApk = (app.apks as? ApksResult.Success)?.apks?.firstOrNull() ?: return

    val installState = installStates[latestApk.uniqueKey] ?: InstallState.Idle
    FloatingActionCard(
        modifier = modifier,
        text = { textModifier ->
            Text(
                text = stringResource(id = R.string.tryfox_card_title, latestApk.version),
                style = MaterialTheme.typography.titleMedium,
                modifier = textModifier,
            )
        },
        action = {
            DownloadButton(
                downloadState = latestApk.downloadState,
                onDownloadClick = { onDownloadClick(latestApk) },
                onInstallClick = { onInstallClick(latestApk) },
                inProgressText = stringResource(id = R.string.download_button_download),
                installState = installState,
                onOpenClick = onOpenInstalledApp,
                debugLabel = "home:${latestApk.uniqueKey}",
            )
        },
        footer = {
            (installState as? InstallState.Failed)?.let { failure ->
                Text(
                    text = failure.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
        },
    )
}
