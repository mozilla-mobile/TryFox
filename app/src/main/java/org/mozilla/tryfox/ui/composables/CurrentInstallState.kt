package org.mozilla.tryfox.ui.composables

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.mozilla.tryfox.R
import org.mozilla.tryfox.model.AppState

@Composable
fun CurrentInstallState(
    appState: AppState?,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            appState == null || !appState.isInstalled -> {
                AssistChip(
                    onClick = { /* No action */ },
                    label = { Text(stringResource(id = R.string.not_installed_chip_label)) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                    border = null,
                )
            }
            else -> {
                AssistChip(
                    onClick = { /* No action */ },
                    label = { Text(stringResource(id = R.string.installed_chip_label)) },
                    border = AssistChipDefaults.assistChipBorder(true),
                )
            }
        }

        // Optional icon indicating the app was installed from the Play Store; tapping it opens
        // the app's store listing.
        if (appState != null && appState.isInstalled && appState.isFromPlayStore) {
            val context = LocalContext.current
            Spacer(modifier = Modifier.width(8.dp))
            Icon(
                painter = painterResource(id = R.drawable.ic_play_store),
                contentDescription = stringResource(id = R.string.play_store_chip_label),
                modifier = Modifier
                    .size(AssistChipDefaults.IconSize)
                    .clickable { openPlayStoreListing(context, appState.packageName) },
            )
        }

        if (appState != null && appState.isInstalled) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "v${appState.version ?: "N/A"} - ${appState.formattedInstallDate ?: "N/A"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Opens the app's Play Store listing in the Play Store app. The icon is only shown when Play was
 * the installer, so Play is expected to be present; if it somehow isn't, the tap is a no-op.
 */
private fun openPlayStoreListing(context: Context, packageName: String) {
    val marketIntent = Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri())
        .setPackage(AppState.PLAY_STORE_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(marketIntent)
    } catch (_: ActivityNotFoundException) {
        // Play Store not available; do nothing.
    }
}
