package org.mozilla.fenixinstaller.ui.screens

import android.content.Context
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import org.mozilla.fenixinstaller.FenixInstallerViewModel
import org.mozilla.fenixinstaller.R
import org.mozilla.fenixinstaller.data.ArtifactUiModel
import org.mozilla.fenixinstaller.data.DownloadState
import org.mozilla.fenixinstaller.data.JobDetailsUiModel
import org.mozilla.fenixinstaller.ui.composables.ArtifactCard
import org.mozilla.fenixinstaller.ui.composables.PushCommentCard
import java.io.File

private const val TAG = "FenixInstallerScreen"

// Project name mappings
private val projectDisplayToActualMap = mapOf(
    "try" to "try",
    "central" to "mozilla-central",
    "beta" to "mozilla-beta",
    "release" to "mozilla-release"
)
private val projectActualToDisplayMap = projectDisplayToActualMap.entries.associate { (k, v) -> v to k }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FenixInstallerApp() {
    val viewModel: FenixInstallerViewModel = viewModel()
    val context = LocalContext.current

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("Fenix Installer") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SearchSection(
                    selectedProject = viewModel.selectedProject, 
                    onProjectSelected = { actualProjectValue -> viewModel.updateSelectedProject(actualProjectValue) },
                    revision = viewModel.revision,
                    onRevisionChange = { viewModel.updateRevision(it) },
                    onSearchClick = { viewModel.searchJobsAndArtifacts(context) },
                    isLoading = viewModel.isLoading && viewModel.selectedJobs.isEmpty() && viewModel.relevantPushComment == null
                )
            }

            if (viewModel.isLoading && viewModel.selectedJobs.isEmpty() && viewModel.relevantPushComment == null) {
                item { LoadingState() }
            }

            viewModel.errorMessage?.let {
                if (viewModel.selectedJobs.isEmpty() || !it.startsWith("Download failed")) {
                    item { ErrorState(errorMessage = it) }
                }
            }

            viewModel.relevantPushComment?.let { comment ->
                if (comment.isNotBlank()) {
                    item { PushCommentCard(comment = comment) }
                }
            }

            if (viewModel.selectedJobs.isNotEmpty()) {
                item {
                    Text(
                        text = "Found ${viewModel.selectedJobs.size} job(s) matching criteria:",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                items(viewModel.selectedJobs, key = { it.taskId }) { job ->
                    JobItem(job = job, viewModel = viewModel)
                }
            } else if (!viewModel.isLoading && viewModel.errorMessage == null && viewModel.relevantPushComment != null) {
                item {
                    Text(
                        "No jobs found matching the specified criteria for this push.",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun AppIcon(appName: String, modifier: Modifier = Modifier) {
    val iconResId = when {
        appName.contains("fenix", ignoreCase = true) -> R.drawable.ic_firefox
        appName.contains("focus", ignoreCase = true) -> R.drawable.ic_focus
        else -> null
    }
    val contentDesc = when {
        appName.contains("fenix", ignoreCase = true) -> "Firefox Icon"
        appName.contains("focus", ignoreCase = true) -> "Focus Icon"
        else -> null
    }

    if (iconResId != null && contentDesc != null) {
        Image(
            painter = painterResource(id = iconResId),
            contentDescription = contentDesc,
            modifier = modifier
        )
        Spacer(modifier = Modifier.width(8.dp))
    }
}

@Composable
fun JobItem(
    job: JobDetailsUiModel,
    viewModel: FenixInstallerViewModel
) {
    val context = LocalContext.current
    val jobArtifacts = job.artifacts
    val (supportedArtifacts, unsupportedArtifacts) = remember(jobArtifacts) {
        jobArtifacts.partition { it.isCompatibleAbi }
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
                    text = "Task ID: ${job.taskId}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (viewModel.isLoadingJobArtifacts[job.taskId] == true && job.artifacts.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Loading artifacts for this job...", style = MaterialTheme.typography.bodyMedium)
                }
            } else if (job.artifacts.isEmpty() && viewModel.isLoadingJobArtifacts[job.taskId] == false) {
                Text("No APKs found for this job.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
            } else {
                if (supportedArtifacts.isNotEmpty()) {
                    Text(
                        text = "APKs supported by your device:",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        supportedArtifacts.forEach { artifactUiModel ->
                            DisplayArtifactCard(artifactUiModel = artifactUiModel, viewModel = viewModel, context = context)
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
                            text = "APKs unsupported by your device (${unsupportedArtifacts.size})",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Icon(
                            imageVector = if (isExpanded) Icons.Filled.ArrowDropUp else Icons.Filled.ArrowDropDown,
                            contentDescription = if (isExpanded) "Collapse" else "Expand"
                        )
                    }
                    if (isExpanded) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 8.dp)) {
                            unsupportedArtifacts.forEach { artifactUiModel ->
                                DisplayArtifactCard(artifactUiModel = artifactUiModel, viewModel = viewModel, context = context)
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
    artifactUiModel: ArtifactUiModel,
    viewModel: FenixInstallerViewModel,
    context: Context
) {
    val fileToInstall = (artifactUiModel.downloadState as? DownloadState.Downloaded)?.file

    Log.d(TAG, "DisplayArtifactCard for ${artifactUiModel.uniqueKey}: downloadState=${artifactUiModel.downloadState}, isCompatible=${artifactUiModel.isCompatibleAbi}")

    if (artifactUiModel.downloadState is DownloadState.DownloadFailed) {
        val errorMessage = (artifactUiModel.downloadState as DownloadState.DownloadFailed).errorMessage
        ErrorState(errorMessage = "Download failed: ${errorMessage ?: "Unknown error"}")
        Spacer(modifier = Modifier.padding(top = 4.dp))
    }

    ArtifactCard(
        artifactUiModel = artifactUiModel,
        onDownloadClick = {
            viewModel.downloadArtifact(artifactUiModel, context)
        },
        onInstallClick = {
            fileToInstall?.let {
                viewModel.onInstallApk?.invoke(it)
            } ?: Log.e(TAG, "InstallClick for ${artifactUiModel.uniqueKey}: No file found to install, state is ${artifactUiModel.downloadState}")
        }
    )
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSection(
    selectedProject: String, 
    onProjectSelected: (String) -> Unit, 
    revision: String,
    onRevisionChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    isLoading: Boolean
) {
    val projectDisplayOptions = projectDisplayToActualMap.keys.toList()
    var expanded by remember { mutableStateOf(false) }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Search Fenix Artifacts",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min), 
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier = Modifier.weight(0.5f).fillMaxHeight()
                ) {
                    TextField(
                        value = projectActualToDisplayMap[selectedProject] ?: selectedProject,
                        onValueChange = {}, 
                        readOnly = true,
                        label = { Text("Project") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors() // Use OutlinedTextFieldDefaults for consistency if desired, or TextFieldDefaults
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        projectDisplayOptions.forEach { displayKey ->
                            DropdownMenuItem(
                                text = { Text(displayKey) },
                                onClick = {
                                    onProjectSelected(projectDisplayToActualMap[displayKey] ?: displayKey) 
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                OutlinedTextField(
                    value = revision,
                    onValueChange = onRevisionChange,
                    label = { Text("Revision") },
                    placeholder = { Text("Revision hash...") },
                    modifier = Modifier.weight(0.5f).fillMaxHeight(),
                    singleLine = true,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                    colors = OutlinedTextFieldDefaults.colors() // Ensure consistent styling
                )

                Button(
                    onClick = onSearchClick,
                    enabled = !isLoading && revision.isNotBlank(),
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 12.dp, bottomEnd = 12.dp), // Adjust corner to match text field or preference
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    contentPadding = PaddingValues(horizontal = 0.dp)
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary 
                        )
                    } else {
                        Icon(
                            Icons.Default.Search, 
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.onPrimary 
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
        Text("Searching for jobs and artifacts...", modifier = Modifier.padding(top = 60.dp))
    }
}

@Composable
fun ErrorState(errorMessage: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = errorMessage,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}
