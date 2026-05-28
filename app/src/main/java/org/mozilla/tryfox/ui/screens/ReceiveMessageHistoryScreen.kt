package org.mozilla.tryfox.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.mozilla.tryfox.R
import org.mozilla.tryfox.lan.LanReceivedMessage
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveMessageHistoryScreen(
    onNavigateUp: () -> Unit,
    onOpenDeepLink: (String) -> Unit,
    receiveMessageHistoryViewModel: ReceiveMessageHistoryViewModel,
    modifier: Modifier = Modifier,
) {
    val history by receiveMessageHistoryViewModel.history.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.lan_receive_message_history_title)) },
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
        if (history.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = stringResource(id = R.string.lan_receive_message_history_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = stringResource(id = R.string.lan_receive_message_history_empty_message),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(history, key = { it.id }) { message ->
                    MessageHistoryCard(
                        message = message,
                        onOpenDeepLink = onOpenDeepLink,
                    )
                }
            }
        }
    }
}

@Composable
private fun MessageHistoryCard(
    message: LanReceivedMessage,
    onOpenDeepLink: (String) -> Unit,
) {
    val deepLink = message.tryfoxDeepLink
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = message.accepted && !deepLink.isNullOrBlank()) {
                if (!deepLink.isNullOrBlank()) {
                    onOpenDeepLink(deepLink)
                }
            },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = if (message.accepted) {
                    stringResource(id = R.string.lan_receive_message_history_status_accepted)
                } else {
                    stringResource(
                        id = R.string.lan_receive_message_history_status_rejected,
                        message.error ?: stringResource(id = R.string.common_unknown_error),
                    )
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            message.revision?.let {
                Text(
                    text = stringResource(id = R.string.lan_receive_last_message_revision, it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            message.author?.let {
                Text(
                    text = stringResource(id = R.string.lan_receive_last_message_author, it),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            message.sourceUrl?.let {
                Text(
                    text = stringResource(id = R.string.lan_receive_message_history_source_url, it),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Text(
                text = stringResource(id = R.string.lan_receive_message_history_received_at, formatMessageTimestamp(message.receivedAt)),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatMessageTimestamp(timestamp: Long): String =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
        .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
