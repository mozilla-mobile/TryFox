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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.R
import org.mozilla.tryfox.data.SearchHistory
import org.mozilla.tryfox.data.SearchHistoryEntry
import org.mozilla.tryfox.install.InstallState
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.composables.BinButton
import org.mozilla.tryfox.ui.composables.ProjectSelector

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
    searchViewModel: SearchViewModel,
    deepLinkProject: String?,
    deepLinkQuery: String?,
    onNavigateUp: () -> Unit,
    searchHistory: List<SearchHistoryEntry> = emptyList(),
) {
    val cacheState by searchViewModel.cacheState.collectAsState()
    val query by searchViewModel.query.collectAsState()
    val selectedProject by searchViewModel.selectedProject.collectAsState()
    val isLoading by searchViewModel.isLoading.collectAsState()
    val errorMessage by searchViewModel.errorMessage.collectAsState()
    val pushes by searchViewModel.pushes.collectAsState()
    val installStates by searchViewModel.installStates.collectAsState()
    val activeInstallKey = installStates.entries.firstOrNull { (_, state) ->
        state is InstallState.Installing || state is InstallState.Uninstalling || state is InstallState.Conflict
    }?.key
    val installConflict = installStates.entries.firstOrNull { (_, state) -> state is InstallState.Conflict }
    val isDownloading = pushes.any { push -> push.jobs.any { job -> job.artifacts.any { it.downloadState is org.mozilla.tryfox.data.DownloadState.InProgress } } }
    var queryValidationError by remember(deepLinkQuery) {
        mutableStateOf(
            deepLinkQuery?.takeIf { SearchQueryClassifier.classify(it).isFailure }
                ?.let { "Enter a valid email address or a revision without @." },
        )
    }
    var hasSubmittedSearch by rememberSaveable(deepLinkQuery) { mutableStateOf(deepLinkQuery != null) }
    var displayedQuery by rememberSaveable(deepLinkQuery) { mutableStateOf(deepLinkQuery.orEmpty()) }
    var isSearchFieldFocused by remember { mutableStateOf(false) }

    installConflict?.let { (artifactKey, state) ->
        val conflict = state as InstallState.Conflict
        AlertDialog(
            onDismissRequest = { searchViewModel.cancelInstallConflict(artifactKey) },
            title = { Text(stringResource(id = R.string.install_conflict_title)) },
            text = { Text(stringResource(R.string.install_conflict_message, conflict.packageName)) },
            confirmButton = {
                Button(onClick = { searchViewModel.confirmUninstallAndRetry(artifactKey) }) {
                    Text(stringResource(id = R.string.install_conflict_confirm))
                }
            },
            dismissButton = {
                Button(onClick = { searchViewModel.cancelInstallConflict(artifactKey) }) {
                    Text(stringResource(id = R.string.install_conflict_cancel))
                }
            },
        )
    }

    val isEditingDisplayedSearch = hasSubmittedSearch &&
        isSearchFieldFocused &&
        query != displayedQuery
    val showSearchHistory = !hasSubmittedSearch || isEditingDisplayedSearch
    val showCurrentSearch = !isEditingDisplayedSearch

    fun submitSearch(queryToSubmit: String) {
        when (SearchQueryClassifier.classify(queryToSubmit).getOrNull()) {
            is SearchQuery.Email, is SearchQuery.Revision -> {
                queryValidationError = null
                hasSubmittedSearch = true
                displayedQuery = queryToSubmit
                searchViewModel.submitSearch()
            }
            null -> queryValidationError = "Enter a valid email address or a revision without @."
        }
    }

    LaunchedEffect(deepLinkProject, deepLinkQuery) {
        deepLinkQuery?.let { searchViewModel.setQueryFromDeepLinkAndSearch(deepLinkProject, it) }
    }

    val binButtonEnabled = !isDownloading && activeInstallKey == null && cacheState == CacheManagementState.IdleNonEmpty

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
                            onConfirm = { searchViewModel.clearAppCache() },
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
                    selectedProject = selectedProject,
                    onProjectSelected = searchViewModel::updateSelectedProject,
                    revision = query,
                    onRevisionChange = {
                        queryValidationError = null
                        // A text edit is unambiguously an editing interaction, including
                        // the trailing clear action whose focus callback can be delayed.
                        isSearchFieldFocused = true
                        searchViewModel.updateQuery(it)
                    },
                    onSearchClick = {
                        submitSearch(query)
                    },
                    isLoading = isLoading,
                    showSearchHistory = showSearchHistory,
                    onSearchFieldFocusChanged = { isSearchFieldFocused = it },
                    searchHistory = SearchHistory.displayOrder(searchHistory),
                    onHistoryItemSelected = { entry ->
                        searchViewModel.updateSelectedProject(entry.project)
                        searchViewModel.updateQuery(entry.query)
                        submitSearch(entry.query)
                    },
                )
            }

            errorMessage?.let {
                // TODO: Consider creating a specific string resource for \"Download failed\" if it's a common prefix for user-facing errors.
                if (pushes.isEmpty() || !it.startsWith("Download failed")) {
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

            if (isLoading) {
                item {
                    AnimatedVisibility(
                        visible = showCurrentSearch,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        LoadingState(candidateCount = 0)
                    }
                }
            } else if (pushes.isNotEmpty()) {
                items(pushes.size) { index ->
                    AnimatedVisibility(
                        visible = showCurrentSearch,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                    ) {
                        PushResultCard(
                            push = pushes[index],
                            onDownloadClick = searchViewModel::downloadArtifact,
                            onInstallClick = searchViewModel::installArtifact,
                            onOpenClick = searchViewModel::openInstalledApp,
                            installStates = installStates,
                            activeInstallKey = activeInstallKey,
                            testTag = "search_push_${pushes[index].revision}",
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

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SearchInputRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    isLoading: Boolean,
    onFocusChanged: (Boolean) -> Unit = {},
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = { Text(stringResource(R.string.profile_screen_user_email_label), maxLines = 1) },
            modifier = Modifier.weight(1f).heightIn(min = 56.dp).onFocusChanged { onFocusChanged(it.isFocused) }.testTag("search_query_input"),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.profile_screen_clear_email_description)) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearchClick(); keyboardController?.hide() }),
        )
        Button(onClick = { onSearchClick(); keyboardController?.hide() }, enabled = !isLoading && query.isNotBlank(), modifier = Modifier.size(52.dp), shape = RoundedCornerShape(24.dp), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary), contentPadding = PaddingValues(0.dp)) {
            if (isLoading) CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            else Icon(Icons.Default.Search, contentDescription = stringResource(R.string.profile_screen_search_button_description))
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
