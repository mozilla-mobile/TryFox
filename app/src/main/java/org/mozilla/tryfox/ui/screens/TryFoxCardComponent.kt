package org.mozilla.tryfox.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.AppUiModel

@Composable
fun TryFoxCardComponent(
    modifier: Modifier = Modifier,
    tryFoxApp: AppUiModel,
    onDownloadClick: (ApkUiModel) -> Unit,
    onInstallClick: (java.io.File) -> Unit,
    onDismiss: () -> Unit,
    onTryFoxCardHeightChange: (Dp) -> Unit,
) {
    SwipeableTryFoxCard(
        modifier = modifier,
        tryFoxApp = tryFoxApp,
        onDownloadClick = onDownloadClick,
        onInstallClick = onInstallClick,
        onDismiss = onDismiss,
        onTryFoxCardHeightChange = onTryFoxCardHeightChange,
    )
}
