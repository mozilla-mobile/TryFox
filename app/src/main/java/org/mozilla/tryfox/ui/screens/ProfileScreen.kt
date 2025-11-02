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
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.R
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.ui.composables.AppIcon
import org.mozilla.tryfox.ui.composables.BinButton
import org.mozilla.tryfox.ui.composables.DownloadButton
import org.mozilla.tryfox.ui.composables.PushCommentCard
import org.mozilla.tryfox.ui.models.JobDetailsUiModel
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_NIGHTLY
import org.mozilla.tryfox.util.FOCUS
import java.util.Locale

// Helper function to format app name for display
private fun formatAppNameForDisplay(appName: String): String {
    return when (appName.lowercase(Locale.getDefault())) {
        FENIX_NIGHTLY -> "Fenix Nightly"
        FENIX -> "Fenix"
        FOCUS -> "Focus"
        else -> appName.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}

@Composable
private fun ProfileSearchButton(
    onClick: () -> Unit,
    enabled: Boolean,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.testTag("profile_search_button"),
        shape = RoundedCornerShape(topStart = 0.dp, bottomStart = 0.dp, topEnd = 8.dp, bottomEnd = 8.dp),
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
    onSearchClick: () -> Unit,
    isLoading: Boolean,
    modifier: Modifier = Modifier,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Card(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(id = R.string.profile_screen_search_card_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = email,
                    onValueChange = onEmailChange,
                    label = { Text(stringResource(id = R.string.profile_screen_user_email_label)) },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("profile_email_input"),
                    singleLine = true,
                    shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = 0.dp, bottomEnd = 0.dp),
                    trailingIcon = {
                        if (email.isNotEmpty()) {
                            IconButton(
                                onClick = { onEmailChange("") },
                                modifier = Modifier.testTag("profile_email_clear_button"),
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = stringResource(id = R.string.profile_screen_clear_email_description),
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        onSearchClick() // Perform the original search action
                        keyboardController?.hide()
                    }),
                )
                ProfileSearchButton(
                    onClick = {
                        onSearchClick() // Perform the original search action
                        keyboardController?.hide()
                    },
                    enabled = !isLoading && email.isNotBlank(),
                    isLoading = isLoading,
                    modifier = Modifier.fillMaxHeight().padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun ErrorState(errorMessage: String, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = errorMessage,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.colorScheme.onErrorContainer,
            style = MaterialTheme.typography.bodyMedium,
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
) {
    val authorEmail by profileViewModel.authorEmail.collectAsState()
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
                onSearchClick = { profileViewModel.searchByAuthor() },
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
                errorMessage != null -> {
                    ErrorState(errorMessage = errorMessage!!)
                }
                pushes.isNotEmpty() -> {
                    LazyColumn(
                        contentPadding = PaddingValues(bottom = 16.dp),
                    ) {
                        items(pushes, key = { push ->
                            push.pushComment + push.author + (push.jobs.firstOrNull()?.taskId ?: "")
                        }) { push ->
                            ElevatedCard(
                                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    PushCommentCard(
                                        comment = push.pushComment,
                                        author = push.author,
                                        revision = push.revision ?: "unknown_revision",
                                    )
                                    push.jobs.forEach { job ->
                                        JobCard(job = job, profileViewModel = profileViewModel)
                                    }
                                }
                            }
                        }
                    }
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
private fun JobCard(
    job: JobDetailsUiModel,
    profileViewModel: ProfileViewModel,
) {
    val appNameForIconAndLogic = job.appName
    val displayAppName = formatAppNameForDisplay(appNameForIconAndLogic)
    val apk = remember(job.artifacts) {
        job.artifacts.firstOrNull { it.abi.isSupported }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                AppIcon(appName = appNameForIconAndLogic, modifier = Modifier.size(40.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = displayAppName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.weight(1f))
                apk?.let {
                    DownloadButton(
                        downloadState = it.downloadState,
                        onDownloadClick = { profileViewModel.downloadArtifact(it) },
                        onInstallClick = { file -> profileViewModel.installApk(file) },
                    )
                }
            }
        }
    }
}
