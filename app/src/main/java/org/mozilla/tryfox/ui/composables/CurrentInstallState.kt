package org.mozilla.tryfox.ui.composables

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import org.mozilla.tryfox.R
import org.mozilla.tryfox.model.AppState

@Composable
fun CurrentInstallState(
    appState: AppState?,
    appDisplayName: String,
    modifier: Modifier = Modifier,
) {
    // Tapping an "installed" chip opens a sheet detailing the installed package.
    var showMetadataSheet by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            appState == null || !appState.isInstalled -> {
                AssistChip(
                    onClick = { /* No action */ },
                    label = { Text(stringResource(id = R.string.not_installed_chip_label)) },
                    border = AssistChipDefaults.assistChipBorder(true),
                )
            }
            else -> {
                AssistChip(
                    onClick = { showMetadataSheet = true },
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
                text = "v${appState.version ?: "N/A"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showMetadataSheet && appState != null && appState.isInstalled) {
        InstallMetadataBottomSheet(
            appState = appState,
            title = appDisplayName,
            onDismiss = { showMetadataSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallMetadataBottomSheet(
    appState: AppState,
    title: String,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp),
            )
            InstallMetadataRow(
                label = stringResource(id = R.string.install_metadata_package_name_label),
                value = appState.packageName,
            )
            InstallMetadataRow(
                label = stringResource(id = R.string.install_metadata_version_label),
                value = appState.version
                    ?: stringResource(id = R.string.install_metadata_value_unknown),
            )
            InstallMetadataRow(
                label = stringResource(id = R.string.install_metadata_version_code_label),
                value = appState.versionCode?.toString()
                    ?: stringResource(id = R.string.install_metadata_value_unknown),
            )
            InstallMetadataRow(
                label = stringResource(id = R.string.install_metadata_install_date_label),
                value = appState.formattedInstallDate
                    ?: stringResource(id = R.string.install_metadata_value_unknown),
            )
            InstallMetadataRow(
                label = stringResource(id = R.string.install_metadata_source_label),
                value = installSourceLabel(appState),
            )
            InstallMetadataBlock(
                label = stringResource(id = R.string.install_metadata_splits_label),
                // splitNames covers only the config splits; the base APK is always present but not
                // listed there, so prepend it. A monolithic (single-APK) install then just shows "base".
                value = (listOf("base") + appState.splitNames).joinToString("\n"),
            )
        }
    }
}

@Composable
private fun installSourceLabel(appState: AppState): String = when {
    appState.isFromPlayStore -> stringResource(id = R.string.install_metadata_source_play_store)
    appState.installingPackageName != null -> appState.installingPackageName
    else -> stringResource(id = R.string.install_metadata_source_sideloaded)
}

@Composable
private fun InstallMetadataRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * A full-width, stacked label/value pair for long or multi-line values (e.g. a signing certificate
 * fingerprint or a list of split APKs) that don't fit the compact side-by-side [InstallMetadataRow].
 */
@Composable
private fun InstallMetadataBlock(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp),
        )
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
