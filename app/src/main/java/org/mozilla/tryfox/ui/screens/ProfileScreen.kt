package org.mozilla.tryfox.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.R
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.composables.AppIcon
import org.mozilla.tryfox.ui.composables.BinButton
import org.mozilla.tryfox.ui.composables.DownloadButton
import org.mozilla.tryfox.ui.composables.ErrorState
import org.mozilla.tryfox.ui.composables.ProjectSelector
import org.mozilla.tryfox.ui.composables.rememberLinkedPushComment
import org.mozilla.tryfox.ui.models.ArtifactUiModel
import org.mozilla.tryfox.ui.models.JobDetailsUiModel
import org.mozilla.tryfox.ui.models.PushUiModel
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_NIGHTLY
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_NIGHTLY
import org.mozilla.tryfox.util.FOCUS_RELEASE
import java.util.Locale

// Helper function to format app name for display
private fun formatAppNameForDisplay(appName: String): String {
    return when (appName.lowercase(Locale.getDefault())) {
        FENIX_NIGHTLY -> "Fenix Nightly"
        FENIX -> "Fenix"
        FOCUS -> "Focus Nightly"
        FOCUS_RELEASE -> "Focus Release"
        else -> appName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

private fun projectDisplayName(project: String): String {
    return when (project) {
        "try" -> "try"
        "mozilla-central" -> "central"
        "mozilla-beta" -> "beta"
        "mozilla-release" -> "release"
        else -> project
    }
}

private val signingApkJobNamePattern = Regex(
    pattern = "signing-apk-(fenix|focus)-(debug|nightly|beta|release)(-(firebase|simulation))?",
    option = RegexOption.IGNORE_CASE,
)

internal fun formatJobNameForDisplay(jobName: String): String {
    val match = signingApkJobNamePattern.matchEntire(jobName.trim()) ?: return jobName
    val appName = when (match.groupValues[1].lowercase(Locale.ROOT)) {
        FENIX -> "Fenix"
        FOCUS -> "Focus"
        else -> return jobName
    }
    val channel = match.groupValues[2].lowercase(Locale.ROOT)
    val variantSuffix = when (match.groupValues[4].lowercase(Locale.ROOT)) {
        "firebase" -> " (firebase)"
        "simulation" -> " (perftests)"
        else -> ""
    }
    return "$appName $channel$variantSuffix"
}

internal fun appIconNameForJob(jobName: String, fallbackAppName: String): String {
    val normalizedJobName = jobName.lowercase(Locale.ROOT)
    return when {
        "focus-debug" in normalizedJobName -> FOCUS
        "focus-nightly" in normalizedJobName -> FOCUS_NIGHTLY
        "focus-beta" in normalizedJobName -> FOCUS_BETA
        "focus" in normalizedJobName -> FOCUS
        "fenix-debug" in normalizedJobName -> FENIX
        "fenix-nightly" in normalizedJobName -> FENIX_NIGHTLY
        "fenix-release" in normalizedJobName -> FENIX_RELEASE
        "fenix-beta" in normalizedJobName -> FENIX_BETA
        else -> fallbackAppName
    }
}

@Composable
internal fun SearchSubmitButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.testTag("profile_search_button"),
    shape = RoundedCornerShape(24.dp),
        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(0.dp),
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp).padding(horizontal = 12.dp),
                color = MaterialTheme.colorScheme.onPrimary,
            )
        } else {
            Icon(
                Icons.Default.Search,
                contentDescription = stringResource(id = R.string.profile_screen_search_button_description),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun UserSearchCard(
    email: String,
    onEmailChange: (String) -> Unit,
    project: String,
    onProjectChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val projects = listOf("try", "mozilla-central", "mozilla-beta", "mozilla-release")
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProjectSelector(
            projects = projects,
            selectedProject = project,
            projectLabel = ::projectDisplayName,
            onProjectSelected = onProjectChange,
            modifier = Modifier.height(52.dp),
        )
        SearchInputRow(
            query = email,
            onQueryChange = onEmailChange,
            onSearchClick = onSearchClick,
            isLoading = isLoading,
        )
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun SearchInputRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onSearchClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text(
                    text = stringResource(id = R.string.profile_screen_user_email_label),
                    maxLines = 1,
                )
            },
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 56.dp)
                .testTag("profile_email_input"),
            singleLine = true,
            shape = RoundedCornerShape(20.dp),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { onQueryChange("") },
                        modifier = Modifier.testTag("profile_email_clear_button"),
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(id = R.string.profile_screen_clear_email_description),
                        )
                    }
                }
            },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Email,
                imeAction = ImeAction.Search,
            ),
            keyboardActions = KeyboardActions(onSearch = {
                onSearchClick()
                keyboardController?.hide()
            }),
        )
        SearchSubmitButton(
            onClick = {
                onSearchClick()
                keyboardController?.hide()
            },
            enabled = !isLoading && query.isNotBlank(),
            isLoading = isLoading,
            modifier = Modifier.size(52.dp),
        )
    }
}

/**
 * Composable function for the Profile screen, which allows users to search for pushes by author email.
 *
 * @param modifier The modifier to be applied to the component.
 * @param onNavigateUp Callback to navigate back to the previous screen.
 * @param profileViewModel The ViewModel for the Profile screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    profileViewModel: ProfileViewModel,
    onSearchRevision: (project: String, revision: String) -> Unit = { _, _ -> },
) {
    val authorEmail by profileViewModel.authorEmail.collectAsState()
    val selectedProject by profileViewModel.selectedProject.collectAsState()
    val pushes by profileViewModel.pushes.collectAsState()
    val isLoading by profileViewModel.isLoading.collectAsState()
    val errorMessage by profileViewModel.errorMessage.collectAsState()
    val cacheState by profileViewModel.cacheState.collectAsState()

    val isDownloading = remember(pushes) {
        pushes.any { push ->
            push.jobs.any { job ->
                job.artifacts.any { artifact ->
                    artifact.downloadState is DownloadState.InProgress
                }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.profile_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
                            onConfirm = { profileViewModel.clearAppCache() },
                            enabled = !isDownloading && cacheState == CacheManagementState.IdleNonEmpty,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
                .padding(top = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            UserSearchCard(
                email = authorEmail,
                onEmailChange = { profileViewModel.updateAuthorEmail(it) },
                project = selectedProject,
                onProjectChange = { profileViewModel.updateSelectedProject(it) },
                onSearchClick = {
                    when (val query = SearchQueryClassifier.classify(authorEmail).getOrNull()) {
                        is SearchQuery.Email -> profileViewModel.searchByAuthor()
                        is SearchQuery.Revision -> onSearchRevision(selectedProject, query.value)
                        null -> profileViewModel.showInvalidQueryError()
                    }
                },
                isLoading = isLoading && pushes.isEmpty(),
            )

            when {
                isLoading && pushes.isEmpty() && authorEmail.isNotBlank() -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = stringResource(id = R.string.profile_screen_loading_pushes),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
                pushes.isNotEmpty() -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        errorMessage?.let { message ->
                            item { ErrorState(errorMessage = message) }
                        }
                        item {
                            Text(
                                text = pluralStringResource(R.plurals.profile_screen_pushes_found, pushes.size, pushes.size),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.testTag("email_search_results_heading"),
                            )
                        }
                        items(pushes, key = { push -> push.revision ?: push.pushComment }) { push ->
                            PushResultCard(
                                push = push,
                                onDownloadClick = profileViewModel::downloadArtifact,
                                onInstallClick = profileViewModel::installApk,
                                testTag = "email_search_push_${push.revision}",
                            )
                        }
                    }
                }
                errorMessage != null -> {
                    ErrorState(errorMessage = errorMessage!!)
                }
                !isLoading && errorMessage == null && pushes.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val message = if (authorEmail.isBlank()) {
                            stringResource(id = R.string.profile_screen_no_pushes_enter_email)
                        } else {
                            stringResource(id = R.string.profile_screen_no_pushes_found)
                        }
                        Text(message)
                    }
                }
            }
        }
    }
}

@Composable
internal fun PushResultCard(
    push: PushUiModel,
    onDownloadClick: (ArtifactUiModel) -> Unit,
    onInstallClick: (java.io.File) -> Unit,
    testTag: String,
) {
    val commitTitle = remember(push.pushComment) {
        push.pushComment.lineSequence().firstOrNull().orEmpty().trim()
            .ifBlank { "Revision ${push.revision?.take(12).orEmpty()}" }
    }
    Card(
        modifier = Modifier.fillMaxWidth().testTag(testTag),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = rememberLinkedPushComment(commitTitle),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (push.pushTimestamp > 0L || push.author.isNotBlank()) {
                Text(
                    text = listOfNotNull(
                        formatRelativePushTime(push.pushTimestamp).takeIf { push.pushTimestamp > 0L },
                        push.author.takeIf(String::isNotBlank),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(top = 14.dp))
            push.jobs.forEachIndexed { index, job ->
                if (index > 0) HorizontalDivider()
                CompactApkRow(
                    job = job,
                    onDownloadClick = onDownloadClick,
                    onInstallClick = onInstallClick,
                )
            }
        }
    }
}

@Composable
private fun CompactApkRow(
    job: JobDetailsUiModel,
    onDownloadClick: (ArtifactUiModel) -> Unit,
    onInstallClick: (java.io.File) -> Unit,
) {
    val apk = remember(job.artifacts) { job.artifacts.firstOrNull { it.abi.isSupported } }
    val appIconName = remember(job.jobName, job.appName) { appIconNameForJob(job.jobName, job.appName) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(
            appName = appIconName,
            modifier = Modifier.size(34.dp),
            useSearchResultVariant = true,
        )
        Text(
            text = job.jobName.ifBlank { formatAppNameForDisplay(job.appName) }.let(::formatJobNameForDisplay),
            style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.Auto),
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        apk?.let {
            DownloadButton(
                downloadState = it.downloadState,
                onDownloadClick = { onDownloadClick(it) },
                onInstallClick = onInstallClick,
                modifier = Modifier.width(112.dp),
                inProgressText = stringResource(id = R.string.download_button_download),
            )
        }
    }
}
