package org.mozilla.tryfox.ui.composables

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
                    label = { Text("Not installed") },
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
                    label = { Text("Installed") },
                    border = AssistChipDefaults.assistChipBorder(true),
                )
            }
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
