package org.mozilla.tryfox.ui.composables

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import org.mozilla.tryfox.R
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.theme.TryFoxTheme

@Composable
fun BinButton(
    cacheState: CacheManagementState,
    onConfirm: () -> Unit,
    enabled: Boolean,
) {
    var isActuallyConfirming by remember { mutableStateOf(false) }
    var timerJob by remember { mutableStateOf<Job?>(null) }

    var showCompletionAnimation by remember { mutableStateOf(false) }
    var completionProgress by remember { mutableFloatStateOf(0f) }
    var completionAlpha by remember { mutableFloatStateOf(1f) }
    var isFillingCompletionProgress by remember { mutableStateOf(false) }

    var previousCacheState by remember { mutableStateOf(cacheState) }

    val iconSize by animateDpAsState(
        targetValue = if (cacheState == CacheManagementState.Clearing || isFillingCompletionProgress) 18.dp else 24.dp,
        animationSpec = tween(durationMillis = 300),
        label = "IconSizeAnimation",
    )

    LaunchedEffect(isActuallyConfirming, timerJob) {
        if (isActuallyConfirming && timerJob == null) {
            val newJob = Job()
            timerJob = newJob
            delay(3000L)
            if (newJob.isActive) {
                isActuallyConfirming = false
            }
            timerJob = null
        } else if (!isActuallyConfirming) {
            timerJob?.cancel()
            timerJob = null
        }
    }

    LaunchedEffect(cacheState) {
        if (previousCacheState == CacheManagementState.Clearing &&
            cacheState != CacheManagementState.Clearing &&
            !showCompletionAnimation
        ) {
            showCompletionAnimation = true
            isFillingCompletionProgress = true
            completionProgress = 0f
            completionAlpha = 1f

            animate(0f, 1f, animationSpec = tween(1000)) { value, _ ->
                completionProgress = value
            }
            isFillingCompletionProgress = false

            delay(200)
            animate(1f, 0f, animationSpec = tween(300)) { value, _ ->
                completionAlpha = value
            }
            showCompletionAnimation = false
        }
        previousCacheState = cacheState
    }

    LaunchedEffect(cacheState, enabled) {
        if (isActuallyConfirming &&
            (
                !enabled || cacheState == CacheManagementState.IdleEmpty ||
                    cacheState == CacheManagementState.Clearing
            )
        ) {
            isActuallyConfirming = false
        }
    }

    val (currentIcon, contentDescription) = when (isActuallyConfirming) {
        true -> Icons.Filled.DeleteForever to R.string.bin_button_confirm_clear_cache_description
        false -> Icons.Outlined.Delete to R.string.bin_button_clear_cache_description
    }

    Box(contentAlignment = Alignment.Center) {
        IconButton(
            onClick = {
                // Click action is only relevant if the button is enabled and cache has items
                // The enabled state itself is controlled by the `enabled` parameter passed to BinButton
                if (isActuallyConfirming) {
                    onConfirm()
                    isActuallyConfirming = false
                } else {
                    isActuallyConfirming = true
                }
            },
            enabled = enabled,
        ) {
            Icon(
                imageVector = currentIcon,
                contentDescription = stringResource(id = contentDescription),
                modifier = Modifier.size(iconSize),
            )
        }

        // Show progress indicator based on cacheState, irrespective of the button's clickable enabled state
        if (cacheState == CacheManagementState.Clearing && !showCompletionAnimation) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        } else if (showCompletionAnimation) {
            CircularProgressIndicator(
                progress = { completionProgress },
                modifier = Modifier
                    .size(24.dp)
                    .alpha(completionAlpha),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 2.dp,
            )
        }
    }
}

@BinButtonPreview
@Composable
fun BinButtonDisabledDownloadingPreview(
    @PreviewParameter(CacheStateProvider::class) state: CacheManagementState,
) {
    TryFoxTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                BinButton(
                    cacheState = state,
                    onConfirm = {},
                    enabled = true,
                )
            }
        }
    }
}

@Preview(
    showBackground = true,
    name = "Completion Cycle Simulation (Button initially enabled)",
    widthDp = 100,
    heightDp = 100,
)
@Composable
fun BinButtonCompletionCyclePreview() {
    TryFoxTheme {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                var currentCacheState by remember {
                    mutableStateOf<CacheManagementState>(
                        CacheManagementState.IdleNonEmpty,
                    )
                }
                // Button is enabled when cache is IdleNonEmpty and not clearing
                val buttonEnabled = currentCacheState == CacheManagementState.IdleNonEmpty

                LaunchedEffect(currentCacheState) {
                    if (currentCacheState == CacheManagementState.Clearing) {
                        delay(2000L)
                        currentCacheState = CacheManagementState.IdleEmpty
                    }
                }

                BinButton(
                    cacheState = currentCacheState,
                    onConfirm = {
                        currentCacheState = CacheManagementState.Clearing
                    },
                    enabled = buttonEnabled,
                )
            }
        }
    }
}

@Preview(widthDp = 100, heightDp = 100, showBackground = true)
annotation class BinButtonPreview

private class CacheStateProvider() : PreviewParameterProvider<CacheManagementState> {
    override val values: Sequence<CacheManagementState> = sequenceOf(
        CacheManagementState.IdleNonEmpty,
        CacheManagementState.Clearing,
        CacheManagementState.IdleEmpty,
    )
}
