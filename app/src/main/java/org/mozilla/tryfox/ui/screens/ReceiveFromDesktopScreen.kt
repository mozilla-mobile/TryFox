package org.mozilla.tryfox.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import org.mozilla.tryfox.R
import org.mozilla.tryfox.lan.LanReceiveStatus
import org.mozilla.tryfox.lan.TryFoxLanReceiveService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiveFromDesktopScreen(
    onNavigateUp: () -> Unit,
    onNavigateToMessageHistory: () -> Unit,
    receiveFromDesktopViewModel: ReceiveFromDesktopViewModel,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val state by receiveFromDesktopViewModel.state.collectAsState()
    var startAfterPermission by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted && startAfterPermission) {
            ContextCompat.startForegroundService(context, TryFoxLanReceiveService.startIntent(context))
        }
        startAfterPermission = false
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(id = R.string.lan_receive_screen_title)) },
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(id = R.string.lan_receive_state_label, state.status.toDisplayName()),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (state.endpoint != null) {
                        Text(
                            text = stringResource(id = R.string.lan_receive_endpoint_label, state.endpoint!!),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state.errorMessage != null) {
                        Text(
                            text = stringResource(id = R.string.lan_receive_error_label, state.errorMessage!!),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (state.status == LanReceiveStatus.LISTENING && state.qrPayloadJson != null) {
                val qrCode = remember(state.qrPayloadJson) {
                    qrCodeBitmap(state.qrPayloadJson!!, sizePx = 768)
                }
                Card(shape = RoundedCornerShape(8.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.lan_receive_qr_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Image(
                            bitmap = qrCode,
                            contentDescription = stringResource(id = R.string.lan_receive_qr_description),
                            modifier = Modifier.size(240.dp),
                        )
                    }
                }
            }

            if (state.lastReceivedMessage != null) {
                val lastMessage = state.lastReceivedMessage!!
                Card(shape = RoundedCornerShape(8.dp)) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = stringResource(id = R.string.lan_receive_last_message_title),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = if (lastMessage.accepted) {
                                stringResource(id = R.string.lan_receive_last_message_accepted)
                            } else {
                                stringResource(
                                    id = R.string.lan_receive_last_message_rejected,
                                    lastMessage.error ?: stringResource(id = R.string.common_unknown_error),
                                )
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        lastMessage.revision?.let {
                            Text(
                                text = stringResource(id = R.string.lan_receive_last_message_revision, it),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        lastMessage.author?.let {
                            Text(
                                text = stringResource(id = R.string.lan_receive_last_message_author, it),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            if (state.status == LanReceiveStatus.LISTENING || state.status == LanReceiveStatus.STARTING) {
                Button(
                    onClick = { context.startService(TryFoxLanReceiveService.stopIntent(context)) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(id = R.string.lan_receive_stop_button))
                }
            } else {
                Button(
                    onClick = {
                        if (notificationsPermissionGranted(context)) {
                            ContextCompat.startForegroundService(context, TryFoxLanReceiveService.startIntent(context))
                        } else {
                            startAfterPermission = true
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(id = R.string.lan_receive_start_button))
                }
            }

            OutlinedButton(
                onClick = onNavigateToMessageHistory,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(id = R.string.lan_receive_message_history_button))
            }
        }
    }
}

private fun notificationsPermissionGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

private fun LanReceiveStatus.toDisplayName(): String =
    when (this) {
        LanReceiveStatus.STOPPED -> "Stopped"
        LanReceiveStatus.STARTING -> "Starting"
        LanReceiveStatus.LISTENING -> "Listening"
        LanReceiveStatus.STOPPING -> "Stopping"
        LanReceiveStatus.ERROR -> "Error"
    }
