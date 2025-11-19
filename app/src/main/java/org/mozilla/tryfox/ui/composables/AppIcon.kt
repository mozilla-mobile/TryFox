package org.mozilla.tryfox.ui.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.R
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.REFERENCE_BROWSER

@Composable
fun AppIcon(appName: String, modifier: Modifier = Modifier) {
    val (iconResId, contentDescResId) = when {
        appName == REFERENCE_BROWSER -> R.drawable.ic_reference_browser to R.string.app_icon_reference_browser_description
        appName == FENIX -> R.drawable.ic_fenix_nightly to R.string.app_icon_firefox_nightly_description
        appName == FENIX_BETA -> R.drawable.ic_firefox_beta to R.string.app_icon_firefox_description
        appName == FENIX_RELEASE -> R.drawable.ic_firefox to R.string.app_icon_firefox_description
        appName == FOCUS -> R.drawable.ic_focus to R.string.app_icon_focus_description
        else -> {
            println("Titouan - Error - $appName")
            null to null
        }
    }

    if (iconResId != null && contentDescResId != null) {
        Image(
            painter = painterResource(id = iconResId),
            contentDescription = stringResource(id = contentDescResId),
            modifier = modifier,
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
}
