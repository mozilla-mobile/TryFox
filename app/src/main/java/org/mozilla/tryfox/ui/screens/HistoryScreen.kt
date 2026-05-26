package org.mozilla.tryfox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toLocalDateTime
import logcat.LogPriority
import logcat.logcat
import org.mozilla.tryfox.R
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.ui.composables.AppIcon
import org.mozilla.tryfox.ui.composables.DownloadButton
import org.mozilla.tryfox.ui.composables.ErrorState
import org.mozilla.tryfox.ui.composables.PushCommentCard
import org.mozilla.tryfox.ui.models.HistoryItemUiModel

internal const val HISTORY_EMPTY_STATE_TAG = "history_empty_state"
internal const val HISTORY_LIST_TAG = "history_list"
private const val HISTORY_SCREEN_LOG_TAG = "HistoryScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    onNavigateUp: () -> Unit,
    onNavigateToTreeherderRevision: (project: String, revision: String) -> Unit,
    historyViewModel: HistoryViewModel,
) {
    val historyItems by historyViewModel.historyItems.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(historyItems.size) {
        logcat(LogPriority.DEBUG, HISTORY_SCREEN_LOG_TAG) {
            "rendered with itemCount=${historyItems.size}"
        }
    }

    DisposableEffect(lifecycleOwner, historyViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                logcat(LogPriority.DEBUG, HISTORY_SCREEN_LOG_TAG) { "ON_RESUME" }
                historyViewModel.refreshCachedDownloadStates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.history_screen_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(id = R.string.common_back_button_description),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
        },
    ) { innerPadding ->
        if (historyItems.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp)
                    .testTag(HISTORY_EMPTY_STATE_TAG),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.history_screen_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(id = R.string.history_screen_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .testTag(HISTORY_LIST_TAG),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(historyItems, key = { it.entry.uniqueKey }) { historyItem ->
                    HistoryCard(
                        historyItem = historyItem,
                        onRevisionClick = onNavigateToTreeherderRevision,
                        onDownloadClick = { historyViewModel.download(historyItem) },
                        onInstallClick = { file -> historyViewModel.install(historyItem, file) },
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryCard(
    historyItem: HistoryItemUiModel,
    onRevisionClick: (project: String, revision: String) -> Unit,
    onDownloadClick: () -> Unit,
    onInstallClick: (java.io.File) -> Unit,
) {
    val entry = historyItem.entry

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIcon(appName = entry.appName, modifier = Modifier.size(36.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = entry.jobName,
                            modifier = Modifier.weight(1f, fill = false),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        JobSymbolChip(symbol = entry.jobSymbol)
                    }
                    Text(
                        text = stringResource(id = R.string.history_screen_revision_label, entry.revision.take(12)),
                        modifier = Modifier.clickable {
                            onRevisionClick(entry.project, entry.revision)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            PushCommentCard(
                comment = entry.commitMessage,
                author = entry.author,
                revision = entry.revision,
                pushTimestamp = entry.pushTimestamp,
            )

            if (historyItem.downloadState is DownloadState.DownloadFailed) {
                ErrorState(
                    errorMessage = stringResource(
                        id = R.string.app_card_download_failed_message,
                        historyItem.downloadState.message ?: stringResource(id = R.string.common_unknown_error),
                    ),
                )
            }

            Text(
                text = stringResource(
                    id = R.string.history_screen_last_installed_label,
                    formatInstallTimestamp(entry.lastInstallerLaunchTimestamp),
                ),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                DownloadButton(
                    downloadState = historyItem.downloadState,
                    onDownloadClick = onDownloadClick,
                    onInstallClick = onInstallClick,
                )
            }
        }
    }
}

@Composable
private fun JobSymbolChip(symbol: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
    ) {
        Text(
            text = symbol,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@OptIn(FormatStringsInDatetimeFormats::class)
@Composable
private fun formatInstallTimestamp(timestampMillis: Long): String {
    val format = remember {
        LocalDateTime.Format { byUnicodePattern("yyyy-MM-dd HH:mm") }
    }
    return remember(timestampMillis) {
        format.format(
            Instant.fromEpochMilliseconds(timestampMillis)
                .toLocalDateTime(TimeZone.currentSystemDefault()),
        )
    }
}
