package org.mozilla.tryfox.ui.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.R
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_NIGHTLY
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_NIGHTLY
import org.mozilla.tryfox.util.FOCUS_RELEASE
import org.mozilla.tryfox.util.REFERENCE_BROWSER

private const val PADDED_FOREGROUND_ICON_SCALE = 1.8f

@Composable
fun AppIcon(
    appName: String,
    modifier: Modifier = Modifier,
    useSearchResultVariant: Boolean = false,
) {
    val (iconResId, contentDescResId) = when {
        appName == REFERENCE_BROWSER -> R.drawable.ic_reference_browser to R.string.app_icon_reference_browser_description
        appName == FENIX -> {
            (if (useSearchResultVariant) R.drawable.ic_fenix_debug_foreground else R.drawable.ic_fenix_nightly) to
                R.string.app_icon_firefox_nightly_description
        }
        appName == FENIX_NIGHTLY -> {
            (if (useSearchResultVariant) R.drawable.ic_fenix_nightly_foreground else R.drawable.ic_fenix_nightly) to
                R.string.app_icon_firefox_nightly_description
        }
        appName == FENIX_BETA -> {
            (if (useSearchResultVariant) R.drawable.ic_fenix_beta_foreground else R.drawable.ic_firefox_beta) to
                R.string.app_icon_firefox_description
        }
        appName == FENIX_RELEASE -> R.drawable.ic_firefox to R.string.app_icon_firefox_description
        appName == FOCUS -> {
            (if (useSearchResultVariant) R.drawable.ic_focus_debug_foreground_v2 else R.drawable.ic_focus) to
                R.string.app_icon_focus_description
        }
        appName == FOCUS_NIGHTLY -> {
            (if (useSearchResultVariant) R.drawable.ic_focus_nightly_foreground else R.drawable.ic_focus) to
                R.string.app_icon_focus_description
        }
        appName == FOCUS_BETA -> {
            (if (useSearchResultVariant) R.drawable.ic_focus_beta_foreground else R.drawable.ic_focus) to
                R.string.app_icon_focus_description
        }
        appName == FOCUS_RELEASE -> R.drawable.ic_focus to R.string.app_icon_focus_description
        else -> {
            println("Titouan - Error - $appName")
            null to null
        }
    }

    if (iconResId != null && contentDescResId != null) {
        val isPaddedSearchResultForeground = useSearchResultVariant && appName in setOf(FENIX, FENIX_BETA, FOCUS_BETA)
        if (isPaddedSearchResultForeground) {
            Box(modifier = modifier.clipToBounds()) {
                Image(
                    painter = painterResource(id = iconResId),
                    contentDescription = stringResource(id = contentDescResId),
                    modifier = Modifier.fillMaxSize().scale(PADDED_FOREGROUND_ICON_SCALE),
                )
            }
        } else {
            Image(
                painter = painterResource(id = iconResId),
                contentDescription = stringResource(id = contentDescResId),
                modifier = modifier,
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
    }
}
