package org.mozilla.tryfox.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.mozilla.tryfox.R
import org.mozilla.tryfox.TryFoxViewModel
import org.mozilla.tryfox.data.SearchHistory
import org.mozilla.tryfox.data.SearchHistoryEntry
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.composables.BinButton
import org.mozilla.tryfox.ui.composables.ProjectSelector
import org.mozilla.tryfox.ui.models.PushUiModel

// Project name mappings
private val projectDisplayToActualMap = mapOf(
    "try" to "try",
    "central" to "mozilla-central",
    "beta" to "mozilla-beta",
    "release" to "mozilla-release",
)

internal const val TREEHERDER_LOADING_STATE_TAG = "treeherder_loading_state"
internal const val TREEHERDER_SEARCH_HISTORY_TAG = "treeherder_search_history"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    tryFoxViewModel: TryFoxViewModel,
    deepLinkProject: String?,
    deepLinkRevision: String?,
    onNavigateUp: () -> Unit,
    onSearchEmail: (project: String, email: String) -> Unit = { _, _ -> },
    searchHistory: List<SearchHistoryEntry> = emptyList(),
    onSearchSucceeded: (project: String, query: String) -> Unit = { _, _ -> },
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
    var hasSubmittedSearch by rememberSaveable(deepLinkRevision) { mutableStateOf(deepLinkRevision != null) }
    var displayedQuery by rememberSaveable(deepLinkRevision) { mutableStateOf(deepLinkRevision.orEmpty()) }
    var isSearchFieldFocused by remember { mutableStateOf(false) }
    var lastRecordedSearchKey by rememberSaveable { mutableStateOf<String?>(null) }

    val isEditingDisplayedSearch = hasSubmittedSearch &&
        isSearchFieldFocused &&
        tryFoxViewModel.revision != displayedQuery
    val showSearchHistory = !hasSubmittedSearch || isEditingDisplayedSearch
    val showCurrentSearch = !isEditingDisplayedSearch

    fun submitSearch(project: String, query: String) {
        when (val searchQuery = SearchQueryClassifier.classify(query).getOrNull()) {
            is SearchQuery.Email -> {
                queryValidationError = null
                hasSubmittedSearch = true
                displayedQuery = query
                onSearchEmail(project, searchQuery.value)
            }
            is SearchQuery.Revision -> {
                queryValidationError = null
                hasSubmittedSearch = true
                displayedQuery = query
                tryFoxViewModel.searchJobsAndArtifacts()
            }
            null -> queryValidationError = "Enter a valid email address or a revision without @."
        }
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

    LaunchedEffect(tryFoxViewModel.successfulSearch) {
        tryFoxViewModel.successfulSearch?.let { search ->
            val searchKey = "${search.project}:${search.query}"
            if (lastRecordedSearchKey != searchKey) {
                onSearchSucceeded(search.project, search.query)
                lastRecordedSearchKey = searchKey
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
                        // A text edit is unambiguously an editing interaction, including
                        // the trailing clear action whose focus callback can be delayed.
                        isSearchFieldFocused = true
                        tryFoxViewModel.updateRevision(it)
                    },
                    onSearchClick = {
                        submitSearch(tryFoxViewModel.selectedProject, tryFoxViewModel.revision)
                    },
                    isLoading = tryFoxViewModel.isLoading,
                    showSearchHistory = showSearchHistory,
                    onSearchFieldFocusChanged = { isSearchFieldFocused = it },
                    searchHistory = SearchHistory.displayOrder(searchHistory),
                    onHistoryItemSelected = { entry ->
                        tryFoxViewModel.updateSelectedProject(entry.project)
                        tryFoxViewModel.updateRevision(entry.query)
                        submitSearch(entry.project, entry.query)
                    },
                )
            }

            tryFoxViewModel.errorMessage?.let {
                // TODO: Consider creating a specific string resource for \"Download failed\" if it's a common prefix for user-facing errors.
                if (tryFoxViewModel.selectedJobs.isEmpty() || !it.startsWith("Download failed")) {
                    item {
                        AnimatedVisibility(
                            visible = showCurrentSearch,
                            enter = fadeIn() + expandVertically(),
                            exit = fadeOut() + shrinkVertically(),
                        ) {
                            ErrorState(errorMessage = it)
                        }
                    }
                }
            }

            queryValidationError?.let { item { ErrorState(errorMessage = it) } }

            if (tryFoxViewModel.isLoading) {
                item {
                    AnimatedVisibility(
                        visible = showCurrentSearch,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        LoadingState(candidateCount = tryFoxViewModel.isLoadingJobArtifacts.size)
                    }
                }
            } else if (tryFoxViewModel.selectedJobs.isNotEmpty()) {
                item {
                    AnimatedVisibility(
                        visible = showCurrentSearch,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        PushResultCard(
                            push = PushUiModel(
                                pushComment = tryFoxViewModel.relevantPushComment.orEmpty(),
                                author = tryFoxViewModel.relevantPushAuthor.orEmpty(),
                                jobs = tryFoxViewModel.selectedJobs,
                                revision = tryFoxViewModel.revision,
                                pushTimestamp = tryFoxViewModel.relevantPushTimestamp ?: 0L,
                            ),
                            onDownloadClick = tryFoxViewModel::downloadArtifact,
                            onInstallClick = tryFoxViewModel::installApk,
                            testTag = "revision_search_push_${tryFoxViewModel.revision}",
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
    searchHistory: List<SearchHistoryEntry> = emptyList(),
    onSearchSucceeded: (project: String, query: String) -> Unit = { _, _ -> },
) = SearchScreen(
    tryFoxViewModel,
    deepLinkProject,
    deepLinkRevision,
    onNavigateUp,
    onSearchEmail,
    searchHistory,
    onSearchSucceeded,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchSection(
    selectedProject: String,
    onProjectSelected: (String) -> Unit,
    revision: String,
    onRevisionChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    isLoading: Boolean,
    showSearchHistory: Boolean = true,
    onSearchFieldFocusChanged: (Boolean) -> Unit = {},
    searchHistory: List<SearchHistoryEntry> = emptyList(),
    onHistoryItemSelected: (SearchHistoryEntry) -> Unit = {},
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
        SearchInputRow(
            query = revision,
            onQueryChange = onRevisionChange,
            onSearchClick = onSearchClick,
            isLoading = isLoading,
            onFocusChanged = onSearchFieldFocusChanged,
        )
        SearchHistoryPanel(
            entries = searchHistory.filter { it.query.contains(revision.trim(), ignoreCase = true) },
            visible = showSearchHistory,
            onEntryClick = onHistoryItemSelected,
        )
    }
}

@Composable
internal fun SearchHistoryPanel(
    entries: List<SearchHistoryEntry>,
    visible: Boolean,
    onEntryClick: (SearchHistoryEntry) -> Unit,
) {
    AnimatedVisibility(
        visible = visible && entries.isNotEmpty(),
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(TREEHERDER_SEARCH_HISTORY_TAG),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            Column {
                Text(
                    text = stringResource(R.string.search_history_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
                entries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEntryClick(entry) }
                            .testTag("treeherder_search_history_$index")
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Default.History,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = entry.query,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = projectDisplayToActualMap.entries.firstOrNull { it.value == entry.project }?.key ?: entry.project,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        )
                    }
                }
            }
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
