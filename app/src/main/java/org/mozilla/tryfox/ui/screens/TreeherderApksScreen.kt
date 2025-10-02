package org.mozilla.tryfox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.R
import org.mozilla.tryfox.TryFoxViewModel
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.composables.AppCard
import org.mozilla.tryfox.ui.composables.BinButton
import org.mozilla.tryfox.ui.composables.PushCommentCard

private const val TAG = "FenixInstallerScreen"

// Project name mappings
private val projectDisplayToActualMap =
    mapOf(
        "try" to "try",
        "central" to "mozilla-central",
        "beta" to "mozilla-beta",
        "release" to "mozilla-release",
    )
private val projectActualToDisplayMap =
    projectDisplayToActualMap.entries.associate { (k, v) -> v to k }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TryFoxMainScreen(
    tryFoxViewModel: TryFoxViewModel,
    onNavigateUp: () -> Unit,
) {
    val cacheState by tryFoxViewModel.cacheState.collectAsState()
    val isDownloading by tryFoxViewModel.isDownloadingAnyFile.collectAsState()

    LaunchedEffect(Unit) {
        tryFoxViewModel.checkCacheStatus()
    }

    val binButtonEnabled = !isDownloading && cacheState == CacheManagementState.IdleNonEmpty

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.app_name)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.common_back_button_description),
                        )
                    }
                },
                actions = {
                    val tooltipState = rememberTooltipState()
                    TooltipBox(
                        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                        tooltip = {
                            PlainTooltip {
                                Text(stringResource(id = R.string.bin_button_tooltip_clear_downloaded_apks))
                            }
                        },
                        state = tooltipState,
                    ) {
                        BinButton(
                            cacheState = cacheState,
                            onConfirm = { tryFoxViewModel.clearAppCache() },
                            enabled = binButtonEnabled,
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer, // Added for consistency
                    ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SearchSection(
                    selectedProject = tryFoxViewModel.selectedProject,
                    onProjectSelected = { actualProjectValue ->
                        tryFoxViewModel.updateSelectedProject(
                            actualProjectValue,
                        )
                    },
                    revision = tryFoxViewModel.revision,
                    onRevisionChange = { tryFoxViewModel.updateRevision(it) },
                    onSearchClick = { tryFoxViewModel.searchJobsAndArtifacts() },
                    isLoading =
                        tryFoxViewModel.isLoading && tryFoxViewModel.selectedJobs.isEmpty() && tryFoxViewModel.relevantPushComment == null,
                )
            }

            if (tryFoxViewModel.isLoading && tryFoxViewModel.selectedJobs.isEmpty() && tryFoxViewModel.relevantPushComment == null) {
                item { LoadingState() }
            }

            tryFoxViewModel.errorMessage?.let {
                // TODO: Consider creating a specific string resource for \"Download failed\" if it's a common prefix for user-facing errors.
                if (tryFoxViewModel.selectedJobs.isEmpty() || !it.startsWith("Download failed")) {
                    item { ErrorState(errorMessage = it) }
                }
            }

            tryFoxViewModel.relevantPushComment?.let { comment ->
                if (comment.isNotBlank() || tryFoxViewModel.relevantPushAuthor != null) { // Show card if comment or author exists
                    item {
                        PushCommentCard(
                            comment = comment ?: "",
                            author = tryFoxViewModel.relevantPushAuthor,
                            revision = tryFoxViewModel.revision, // Added revision
                        )
                    }
                }
            }

            if (tryFoxViewModel.selectedJobs.isNotEmpty()) {
                item {
                    Text(
                        text =
                            stringResource(
                                id = R.string.treeherder_apks_jobs_found_message,
                                tryFoxViewModel.selectedJobs.size,
                            ),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }

                items(tryFoxViewModel.selectedJobs, key = { it.taskId }) { job ->
                    AppCard(job = job, viewModel = tryFoxViewModel)
                }
            } else if (!tryFoxViewModel.isLoading &&
                tryFoxViewModel.errorMessage == null &&
                (tryFoxViewModel.relevantPushComment != null || tryFoxViewModel.relevantPushAuthor != null)
            ) {
                // Slightly adjusted logic to account for author possibly being present even if comment is not
                if (tryFoxViewModel.relevantPushComment?.isNotBlank() == true || tryFoxViewModel.relevantPushAuthor != null) {
                    // This case should ideally be handled by the PushCommentCard itself not rendering if both are empty/null
                } else {
                    item {
                        Text(
                            stringResource(id = R.string.treeherder_apks_no_jobs_found),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSection(
    selectedProject: String,
    onProjectSelected: (String) -> Unit,
    revision: String,
    onRevisionChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    isLoading: Boolean,
) {
    val projectDisplayOptions = projectDisplayToActualMap.keys.toList()
    var expanded by remember { mutableStateOf(false) }

    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.treeherder_apks_search_artifacts_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded },
                    modifier =
                        Modifier
                            .weight(0.5f)
                            .fillMaxHeight(),
                ) {
                    TextField(
                        value = projectActualToDisplayMap[selectedProject] ?: selectedProject,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(id = R.string.treeherder_apks_project_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier =
                            Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        projectDisplayOptions.forEach { displayKey ->
                            DropdownMenuItem(
                                text = { Text(displayKey) },
                                onClick = {
                                    onProjectSelected(
                                        projectDisplayToActualMap[displayKey] ?: displayKey,
                                    )
                                    expanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.width(8.dp))

                OutlinedTextField(
                    value = revision,
                    onValueChange = onRevisionChange,
                    label = { Text(stringResource(id = R.string.treeherder_apks_revision_label)) },
                    placeholder = { Text(stringResource(id = R.string.treeherder_apks_revision_placeholder)) },
                    modifier =
                        Modifier
                            .weight(0.5f)
                            .fillMaxHeight(),
                    singleLine = true,
                    shape =
                        RoundedCornerShape(
                            topStart = 8.dp,
                            bottomStart = 8.dp,
                            topEnd = 0.dp,
                            bottomEnd = 0.dp,
                        ),
                    colors = OutlinedTextFieldDefaults.colors(),
                )

                SearchButton(
                    // Using the same SearchButton as ProfileScreen
                    onClick = onSearchClick,
                    enabled = !isLoading && revision.isNotBlank(),
                    isLoading = isLoading,
                    modifier =
                        Modifier
                            .padding(top = 8.dp)
                            .fillMaxHeight(),
                )
            }
        }
    }
}

// Re-using the SearchButton from ProfileScreen implies it's either moved to a common composables location or defined here.
// For now, assuming it's defined in this file or accessible. If it was meant to be the ProfileScreen.SearchButton,
// this would need refactoring to a common composable. The current `SearchButton` defined below seems tailored for this screen.
@Composable
fun SearchButton(
    // This is the local SearchButton
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape =
            RoundedCornerShape(
                topStart = 0.dp,
                bottomStart = 0.dp,
                topEnd = 12.dp,
                bottomEnd = 12.dp,
            ),
        // Shape from Treeherder
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = 0.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(id = R.string.treeherder_apks_search_button_description), // Specific description
                tint = MaterialTheme.colorScheme.onPrimary,
            )
        }
    }
}

@Composable
fun LoadingState() {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator()
        Text(
            stringResource(id = R.string.treeherder_apks_loading_message),
            modifier = Modifier.padding(top = 60.dp),
        )
    }
}

@Composable
fun ErrorState(errorMessage: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = errorMessage,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}
