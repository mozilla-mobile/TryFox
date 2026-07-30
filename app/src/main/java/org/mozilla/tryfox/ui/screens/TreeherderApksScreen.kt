package org.mozilla.tryfox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.mozilla.tryfox.R
import org.mozilla.tryfox.TryFoxViewModel
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.composables.AppCard
import org.mozilla.tryfox.ui.composables.BinButton
import org.mozilla.tryfox.ui.composables.ErrorState
import org.mozilla.tryfox.ui.composables.ProjectSelector
import org.mozilla.tryfox.ui.composables.PushCommentCard

// Project name mappings
private val projectDisplayToActualMap = mapOf(
    "try" to "try",
    "central" to "mozilla-central",
    "beta" to "mozilla-beta",
    "release" to "mozilla-release",
)

internal const val TREEHERDER_LOADING_STATE_TAG = "treeherder_loading_state"
internal const val TREEHERDER_RESULTS_HEADER_TAG = "treeherder_results_header"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    tryFoxViewModel: TryFoxViewModel,
    deepLinkProject: String?,
    deepLinkRevision: String?,
    onNavigateUp: () -> Unit,
    onSearchEmail: (project: String, email: String) -> Unit = { _, _ -> },
) {
    val cacheState by tryFoxViewModel.cacheState.collectAsState()
    val isDownloading by tryFoxViewModel.isDownloadingAnyFile.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    var queryValidationError by remember(deepLinkRevision) {
        mutableStateOf(
            deepLinkRevision?.takeIf { SearchQueryClassifier.classify(it).isFailure }
                ?.let { "Enter a valid email address or a revision without @." },
        )
    }

    LaunchedEffect(Unit) {
        tryFoxViewModel.checkCacheStatus()
    }

    DisposableEffect(lifecycleOwner, tryFoxViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                tryFoxViewModel.checkCacheStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(deepLinkProject, deepLinkRevision) {
        val revisionQuery = SearchQueryClassifier.classify(deepLinkRevision.orEmpty()).getOrNull() as? SearchQuery.Revision
        if (revisionQuery != null) {
            val resolvedProject = deepLinkProject ?: "try"
            val projectChanged = tryFoxViewModel.selectedProject != resolvedProject
            val revisionChanged = tryFoxViewModel.revision != revisionQuery.value
            if (projectChanged || revisionChanged) {
                tryFoxViewModel.setRevisionFromDeepLinkAndSearch(resolvedProject, revisionQuery.value)
            }
        }
    }

    val binButtonEnabled = !isDownloading && cacheState == CacheManagementState.IdleNonEmpty

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.profile_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(id = R.string.common_back_button_description))
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer, // Added for consistency
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                SearchSection(
                    selectedProject = tryFoxViewModel.selectedProject,
                    onProjectSelected = { actualProjectValue -> tryFoxViewModel.updateSelectedProject(actualProjectValue) },
                    revision = tryFoxViewModel.revision,
                    onRevisionChange = {
                        queryValidationError = null
                        tryFoxViewModel.updateRevision(it)
                    },
                    onSearchClick = {
                        when (val query = SearchQueryClassifier.classify(tryFoxViewModel.revision).getOrNull()) {
                            is SearchQuery.Email -> {
                                queryValidationError = null
                                onSearchEmail(tryFoxViewModel.selectedProject, query.value)
                            }
                            is SearchQuery.Revision -> {
                                queryValidationError = null
                                tryFoxViewModel.searchJobsAndArtifacts()
                            }
                            null -> queryValidationError = "Enter a valid email address or a revision without @."
                        }
                    },
                    isLoading = tryFoxViewModel.isLoading,
                )
            }

            tryFoxViewModel.errorMessage?.let {
                // TODO: Consider creating a specific string resource for \"Download failed\" if it's a common prefix for user-facing errors.
                if (tryFoxViewModel.selectedJobs.isEmpty() || !it.startsWith("Download failed")) {
                    item { ErrorState(errorMessage = it) }
                }
            }

            queryValidationError?.let { item { ErrorState(errorMessage = it) } }

            tryFoxViewModel.relevantPushComment?.let { comment ->
                val pushTimestamp = tryFoxViewModel.relevantPushTimestamp
                if ((comment.isNotBlank() || tryFoxViewModel.relevantPushAuthor != null) && pushTimestamp != null) {
                    item {
                        PushCommentCard(
                            comment = comment,
                            author = tryFoxViewModel.relevantPushAuthor,
                            revision = tryFoxViewModel.revision,
                            pushTimestamp = pushTimestamp,
                        )
                    }
                }
            }

            if (tryFoxViewModel.isLoading) {
                item {
                    LoadingState(candidateCount = tryFoxViewModel.isLoadingJobArtifacts.size)
                }
            } else if (tryFoxViewModel.selectedJobs.isNotEmpty()) {
                item {
                    Text(
                        text = stringResource(id = R.string.treeherder_apks_jobs_found_message, tryFoxViewModel.selectedJobs.size),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(bottom = 8.dp)
                            .testTag(TREEHERDER_RESULTS_HEADER_TAG),
                    )
                }

                items(tryFoxViewModel.selectedJobs, key = { it.taskId }) { job ->
                    AppCard(job = job, viewModel = tryFoxViewModel)
                }
            } else if (!tryFoxViewModel.isLoading && tryFoxViewModel.errorMessage == null && (tryFoxViewModel.relevantPushComment != null || tryFoxViewModel.relevantPushAuthor != null)) {
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

/** Backwards-compatible name retained for callers and existing UI tests. */
@Composable
fun TryFoxMainScreen(
    tryFoxViewModel: TryFoxViewModel,
    deepLinkProject: String?,
    deepLinkRevision: String?,
    onNavigateUp: () -> Unit,
    onSearchEmail: (project: String, email: String) -> Unit = { _, _ -> },
) = SearchScreen(tryFoxViewModel, deepLinkProject, deepLinkRevision, onNavigateUp, onSearchEmail)

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
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ProjectSelector(
            projects = projectDisplayOptions,
            selectedProject = projectDisplayToActualMap.entries.first { it.value == selectedProject }.key,
            projectLabel = { it },
            onProjectSelected = { displayProject -> onProjectSelected(projectDisplayToActualMap.getValue(displayProject)) },
            modifier = Modifier.height(52.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = revision,
                onValueChange = onRevisionChange,
                placeholder = { Text(stringResource(id = R.string.profile_screen_user_email_label)) },
                modifier = Modifier.weight(1f).height(52.dp).testTag("profile_email_input"),
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                colors = OutlinedTextFieldDefaults.colors(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Email),
            )
            SearchButton(
                onClick = onSearchClick,
                enabled = !isLoading && revision.isNotBlank(),
                isLoading = isLoading,
                modifier = Modifier.size(52.dp).testTag("profile_search_button"),
            )
        }
    }
}

// Re-using the SearchButton from ProfileScreen implies it's either moved to a common composables location or defined here.
// For now, assuming it's defined in this file or accessible. If it was meant to be the ProfileScreen.SearchButton,
// this would need refactoring to a common composable. The current `SearchButton` defined below seems tailored for this screen.
@Composable
fun SearchButton( // This is the local SearchButton
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
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
fun LoadingState(candidateCount: Int) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(TREEHERDER_LOADING_STATE_TAG),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                strokeWidth = 3.dp,
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = "Loading signed APKs",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = if (candidateCount > 0) {
                        "Inspecting $candidateCount candidate job${if (candidateCount == 1) "" else "s"} and resolving APK artifacts."
                    } else {
                        stringResource(id = R.string.treeherder_apks_loading_message)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
            )
        }
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
