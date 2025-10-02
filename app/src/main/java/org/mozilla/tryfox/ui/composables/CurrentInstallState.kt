package org.mozilla.tryfox.ui.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.model.AppState
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun CurrentInstallState(
    appState: AppState?,
    apkDisplayDateString: String,
    modifier: Modifier = Modifier,
) {
    val apkDateMillis: Long =
        remember(apkDisplayDateString) {
            try {
                val parser = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
                parser.parse(apkDisplayDateString)?.time ?: 0L
            } catch (_: Exception) {
                0L // Fallback in case of parsing error
            }
        }

    Row(
        modifier = modifier.padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            appState == null || !appState.isInstalled -> {
                AssistChip(
                    onClick = { /* No action */ },
                    label = { Text("Not installed") },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            labelColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    border = null,
                )
            }
            apkDateMillis == 0L || appState.installDateMillis == null -> {
                AssistChip(
                    onClick = { /* No action */ },
                    label = { Text("Installed") },
                    border = AssistChipDefaults.assistChipBorder(true),
                )
            }
            appState.installDateMillis >= apkDateMillis -> {
                AssistChip(
                    onClick = { /* No action */ },
                    label = { Text("Up to date") },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            labelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    border = null,
                )
            }
            else -> {
                val diffMillis = apkDateMillis - appState.installDateMillis
                val daysBehind = TimeUnit.MILLISECONDS.toDays(diffMillis)
                val hoursBehind = TimeUnit.MILLISECONDS.toHours(diffMillis)

                val timeBehindText =
                    when {
                        daysBehind > 31 -> "${daysBehind / 7} weeks behind"
                        daysBehind > 0 -> "$daysBehind days behind"
                        hoursBehind > 0 -> "$hoursBehind hours behind"
                        else -> "Less than an hour behind"
                    }
                AssistChip(
                    onClick = { /* No action */ },
                    label = { Text(timeBehindText) },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Warning",
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    },
                    colors =
                        AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            labelColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        ),
                    border = null,
                )
            }
        }

        if (appState != null && appState.isInstalled) {
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "v${appState.version ?: "N/A"} - ${appState.formattedInstallDate ?: "N/A"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
