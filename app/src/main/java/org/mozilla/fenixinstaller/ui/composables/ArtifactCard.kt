package org.mozilla.fenixinstaller.ui.composables

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.mozilla.fenixinstaller.data.ArtifactUiModel // Changed import
import org.mozilla.fenixinstaller.data.DownloadState // Added import

@OptIn(ExperimentalMaterial3Api::class) // Needed for AssistChip
@Composable
fun ArtifactCard(
    artifactUiModel: ArtifactUiModel, // Changed parameter to ArtifactUiModel
    onDownloadClick: () -> Unit,
    onInstallClick: () -> Unit
) {
    // Derive state from artifactUiModel
    val isDownloading = artifactUiModel.downloadState is DownloadState.InProgress
    val downloadProgress = (artifactUiModel.downloadState as? DownloadState.InProgress)?.progress
    val fileExists = artifactUiModel.downloadState is DownloadState.Downloaded
    val isUnsupportedDeviceAbi = !artifactUiModel.isCompatibleAbi

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = artifactUiModel.name.substringAfterLast("/"), // Use artifactUiModel
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.width(8.dp))

                when (artifactUiModel.downloadState) {
                    is DownloadState.Downloaded -> {
                        Button(onClick = onInstallClick) {
                            Text("Install")
                        }
                    }
                    is DownloadState.InProgress -> {
                        Button(onClick = {}, enabled = false) { // Disable button while downloading
                            if (downloadProgress != null && downloadProgress > 0f) {
                                CircularProgressIndicator(
                                    progress = { downloadProgress }, // Progress from state
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                    strokeWidth = 2.dp
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(ButtonDefaults.IconSize),
                                    strokeWidth = 2.dp
                                )
                            }
                            Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                            Text("Downloading...")
                        }
                    }
                    is DownloadState.NotDownloaded, is DownloadState.DownloadFailed -> {
                        Button(onClick = onDownloadClick) {
                            Text("Download")
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start, 
                verticalAlignment = Alignment.CenterVertically
            ) {
                artifactUiModel.abi?.takeIf { it.isNotBlank() }?.let { // Use artifactUiModel
                    AssistChip(
                        onClick = { /* No action */ },
                        label = { Text(it, style = MaterialTheme.typography.labelSmall) },
                        leadingIcon = {
                            if (isUnsupportedDeviceAbi) { // Use derived state
                                Icon(
                                    Icons.Filled.Warning,
                                    contentDescription = "Warning: Unsupported ABI",
                                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        },
                        colors = AssistChipDefaults.assistChipColors(
                            containerColor = if (isUnsupportedDeviceAbi) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = if (isUnsupportedDeviceAbi) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))

                Text(
                    text = "Expires: ${artifactUiModel.expires.substringBefore('T')}", // Use artifactUiModel
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
