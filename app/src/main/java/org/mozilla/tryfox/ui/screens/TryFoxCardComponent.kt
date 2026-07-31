package org.mozilla.tryfox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.mozilla.tryfox.install.InstallState
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.AppUiModel

@Composable
fun TryFoxCardComponent(
    modifier: Modifier = Modifier,
    tryFoxApp: AppUiModel,
    onDownloadClick: (ApkUiModel) -> Unit,
    onInstallClick: (ApkUiModel) -> Unit,
    installStates: Map<String, InstallState>,
    onOpenInstalledApp: (String) -> Unit,
    onDismiss: () -> Unit,
    onTryFoxCardHeightChange: (Dp) -> Unit,
) {
    SwipeableTryFoxCard(
        modifier = modifier,
        tryFoxApp = tryFoxApp,
        onDownloadClick = onDownloadClick,
        onInstallClick = onInstallClick,
        installStates = installStates,
        onOpenInstalledApp = onOpenInstalledApp,
        onDismiss = onDismiss,
        onTryFoxCardHeightChange = onTryFoxCardHeightChange,
    )
}
