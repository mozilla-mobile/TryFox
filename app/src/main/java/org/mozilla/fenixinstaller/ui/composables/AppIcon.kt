package org.mozilla.fenixinstaller.ui.composables

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.mozilla.fenixinstaller.R

@Composable
fun AppIcon(appName: String, modifier: Modifier = Modifier) {
    val (iconResId, contentDescResId) = when {
        appName.contains("fenix-nightly", ignoreCase = true) -> R.drawable.ic_fenix_nightly to R.string.app_icon_firefox_nightly_description
        appName.contains("fenix", ignoreCase = true) -> R.drawable.ic_firefox to R.string.app_icon_firefox_description
        appName.contains("focus", ignoreCase = true) -> R.drawable.ic_focus to R.string.app_icon_focus_description
        else -> null to null
    }

    if (iconResId != null && contentDescResId != null) {
        Image(
            painter = painterResource(id = iconResId),
            contentDescription = stringResource(id = contentDescResId),
            modifier = modifier
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
}
