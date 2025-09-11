package org.mozilla.fenixinstaller.ui.composables

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mozilla.fenixinstaller.FenixInstallerViewModel
import org.mozilla.fenixinstaller.R
import org.mozilla.fenixinstaller.data.DownloadState
import org.mozilla.fenixinstaller.ui.models.ArtifactUiModel
import org.mozilla.fenixinstaller.ui.models.JobDetailsUiModel
import org.mozilla.fenixinstaller.ui.screens.ErrorState

@Composable
fun AppCard(
    job: JobDetailsUiModel,
    viewModel: FenixInstallerViewModel
) {
    val context = LocalContext.current
    val jobArtifacts = job.artifacts
    val (supportedArtifacts, unsupportedArtifacts) = remember(jobArtifacts) {
        jobArtifacts.partition { it.abi.isSupported }
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 4.dp)
            ) {
                AppIcon(appName = job.appName, modifier = Modifier.size(24.dp))
                Text(
                    text = job.jobName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                AssistChip(
                    onClick = { /* No action needed */ },
                    label = { Text(job.jobSymbol, style = MaterialTheme.typography.labelSmall) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        labelColor = MaterialTheme.colorScheme.onTertiaryContainer
                    )
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(id = R.string.app_card_task_id, job.taskId),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (viewModel.isLoadingJobArtifacts[job.taskId] == true && job.artifacts.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        stringResource(id = R.string.app_card_loading_artifacts),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (job.artifacts.isEmpty() && viewModel.isLoadingJobArtifacts[job.taskId] == false) {
                Text(
                    stringResource(id = R.string.app_card_no_apks_found),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp)
                )
            } else {
                if (supportedArtifacts.isNotEmpty()) {
                    Text(
                        text = stringResource(id = R.string.app_card_supported_apks_title),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        supportedArtifacts.forEach { artifactUiModel ->
                            DisplayArtifactCard(
                                artifact = artifactUiModel,
                                viewModel = viewModel
                            )
                        }
                    }
                }

                if (unsupportedArtifacts.isNotEmpty()) {
                    var isExpanded by remember { mutableStateOf(false) }
                    val topPadding = if (supportedArtifacts.isNotEmpty()) 12.dp else 0.dp
                    Spacer(modifier = Modifier.padding(top = topPadding))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isExpanded = !isExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(id = R.string.app_card_unsupported_apks_title, unsupportedArtifacts.size),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = if (isExpanded) stringResource(id = R.string.app_card_collapse_description) else stringResource(id = R.string.app_card_expand_description)
                        )
                    }
                    if (isExpanded) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            unsupportedArtifacts.forEach { artifactUiModel ->
                                DisplayArtifactCard(
                                    artifact = artifactUiModel,
                                    viewModel = viewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DisplayArtifactCard(
    artifact: ArtifactUiModel,
    viewModel: FenixInstallerViewModel
) {
    if (artifact.downloadState is DownloadState.DownloadFailed) {
        val errorMessage = (artifact.downloadState as DownloadState.DownloadFailed).errorMessage
        ErrorState(errorMessage = stringResource(id = R.string.app_card_download_failed_message, errorMessage ?: stringResource(id = R.string.common_unknown_error)))
        Spacer(modifier = Modifier.padding(top = 4.dp))
    }

    ArtifactCard(
        downloadState = artifact.downloadState,
        abi = artifact.abi,
        onDownloadClick = {
            viewModel.downloadArtifact(artifact)
        },
        onInstallClick = { viewModel.onInstallApk?.invoke(it) }
    )
}
