package org.mozilla.tryfox.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.ui.composables.TryFoxCard
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.AppUiModel
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableTryFoxCard(
    modifier: Modifier = Modifier,
    tryFoxApp: AppUiModel,
    onDownloadClick: (ApkUiModel) -> Unit,
    onInstallClick: (java.io.File) -> Unit,
    onDismiss: () -> Unit,
    onTryFoxCardHeightChange: (Dp) -> Unit,
) {
    val density = LocalDensity.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = {
            if (it == SwipeToDismissBoxValue.Settled) {
                false
            } else {
                onDismiss()
                true
            }
        },
    )

    AnimatedVisibility(
        visible = dismissState.currentValue == SwipeToDismissBoxValue.Settled,
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(animationSpec = tween(durationMillis = 300)),
        modifier = modifier,
    ) {
        SwipeToDismissBox(
            state = dismissState,
            backgroundContent = {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Color.Transparent)
                ) {}
            },
            content = {
                TryFoxCard(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .onGloballyPositioned {
                            onTryFoxCardHeightChange(with(density) { it.size.height.toDp() })
                        }
                        .graphicsLayer {
                            val progress = dismissState.progress
                            val targetValue = dismissState.targetValue
                            val alphaValue = if (targetValue != SwipeToDismissBoxValue.Settled) {
                                1f - abs(progress)
                            } else {
                                1f
                            }
                            alpha = alphaValue
                        },
                    app = tryFoxApp,
                    onDownloadClick = onDownloadClick,
                    onInstallClick = onInstallClick,
                )
            },
        )
    }
}
