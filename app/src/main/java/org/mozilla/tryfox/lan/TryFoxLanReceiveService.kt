package org.mozilla.tryfox.lan

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.contentType
import io.ktor.server.request.receiveStream
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.serialization.kotlinx.json.json
import java.io.IOException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mozilla.tryfox.EXTRA_NAVIGATION_ROUTE
import org.mozilla.tryfox.MainActivity
import org.mozilla.tryfox.R

class TryFoxLanReceiveService : Service(), KoinComponent {
    private val identityManager: LanReceiveIdentityManager by inject()
    private val messageHistoryRepository: LanMessageHistoryRepository by inject()
    private val stateRepository: LanReceiveStateRepository by inject()

    private val serviceScope = kotlinx.coroutines.CoroutineScope(
        kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
    )
    private var server: io.ktor.server.engine.EmbeddedServer<*, *>? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                serviceScope.launch {
                    stopReceiver()
                    stopSelf(startId)
                }
            }

            else -> {
                startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.lan_receive_notification_starting)))
                serviceScope.launch {
                    startReceiverIfNeeded()
                }
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        val currentServer = server
        if (currentServer != null) {
            currentServer.stop(gracePeriodMillis = 1_000, timeoutMillis = 2_000)
            server = null
        }
        if (stateRepository.state.value.status != LanReceiveStatus.ERROR) {
            stateRepository.set(LanReceiveSessionState(status = LanReceiveStatus.STOPPED))
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        serviceScope.cancel()
        super.onDestroy()
    }

    private suspend fun startReceiverIfNeeded() {
        if (server != null) {
            val state = stateRepository.state.value
            updateNotification(
                state.endpoint?.let { getString(R.string.lan_receive_notification_listening, it) }
                    ?: getString(R.string.lan_receive_notification_starting),
            )
            return
        }

        stateRepository.set(
            LanReceiveSessionState(
                status = LanReceiveStatus.STARTING,
            ),
        )

        val identity = identityManager.getOrCreateIdentity()
        val host = resolveLanIpv4Address()
        if (host == null) {
            failStartup("no-lan-address")
            return
        }

        val sessionExpiresAt = System.currentTimeMillis() + SESSION_DURATION_MS
        val validator = LanRequestValidator(identity = identity, sessionExpiresAt = sessionExpiresAt)

        val boundServer = (DEFAULT_PORT..LAST_PORT).firstNotNullOfOrNull { port ->
            val endpoint = "http://$host:$port$MESSAGE_PATH"
            val qrPayloadJson = LanJson.encodeToString(
                LanQrPayload(
                    version = 1,
                    mode = QR_MODE,
                    deviceId = identity.deviceId,
                    deviceName = identity.deviceName,
                    endpoint = endpoint,
                    sharedSecret = identity.sharedSecret,
                    expiresAt = sessionExpiresAt,
                ),
            )

            val candidate = embeddedServer(CIO, host = host, port = port) {
                install(ContentNegotiation) {
                    json(LanJson)
                }
                routing {
                    post(MESSAGE_PATH) {
                        if (call.request.contentType().withoutParameters() != ContentType.Application.Json) {
                            val storedMessage = messageHistoryRepository.record(
                                LanReceivedMessage(
                                    receivedAt = System.currentTimeMillis(),
                                    accepted = false,
                                    error = "invalid-content-type",
                                ),
                            )
                            stateRepository.update {
                                it.copy(lastReceivedMessage = storedMessage)
                            }
                            call.respond(
                                HttpStatusCode.BadRequest,
                                LanReceiveErrorResponse(ok = false, error = "invalid-content-type"),
                            )
                            return@post
                        }

                        val bodyBytes = call.receiveStream().readBytes()
                        when (
                            val validation = validator.validate(
                                headers = mapOf(
                                    "X-Tryfox-Device-Id" to call.request.headers["X-Tryfox-Device-Id"].orEmpty(),
                                    "X-Tryfox-Extension-Id" to call.request.headers["X-Tryfox-Extension-Id"].orEmpty(),
                                    "X-Tryfox-Timestamp" to call.request.headers["X-Tryfox-Timestamp"].orEmpty(),
                                    "X-Tryfox-Nonce" to call.request.headers["X-Tryfox-Nonce"].orEmpty(),
                                    "X-Tryfox-Signature" to call.request.headers["X-Tryfox-Signature"].orEmpty(),
                                ),
                                bodyBytes = bodyBytes,
                            )
                        ) {
                            is LanValidationResult.Failure -> {
                                val storedMessage = messageHistoryRepository.record(
                                    LanReceivedMessage(
                                        receivedAt = System.currentTimeMillis(),
                                        accepted = false,
                                        error = validation.errorCode,
                                        bodyHash = sha256Base64Url(bodyBytes),
                                    ),
                                )
                                stateRepository.update {
                                    it.copy(lastReceivedMessage = storedMessage)
                                }
                                postReceivedMessageNotification(storedMessage)
                                call.respond(
                                    validation.statusCode,
                                    LanReceiveErrorResponse(ok = false, error = validation.errorCode),
                                )
                            }

                            is LanValidationResult.Success -> {
                                val message = validation.message
                                val storedMessage = messageHistoryRepository.record(
                                    LanReceivedMessage(
                                        receivedAt = System.currentTimeMillis(),
                                        accepted = true,
                                        messageId = message.messageId,
                                        extensionId = validation.extensionId,
                                        sourceUrl = message.sourceUrl,
                                        tryfoxDeepLink = message.tryfoxDeepLink,
                                        repo = message.repo,
                                        revision = message.revision,
                                        author = message.author,
                                        bodyHash = validation.bodyHash,
                                    ),
                                )
                                stateRepository.update {
                                    it.copy(lastReceivedMessage = storedMessage)
                                }
                                updateNotification(
                                    getString(R.string.lan_receive_notification_last_message, message.messageId),
                                )
                                postReceivedMessageNotification(storedMessage)
                                call.respond(
                                    HttpStatusCode.OK,
                                    LanReceiveSuccessResponse(ok = true, messageId = message.messageId),
                                )
                            }
                        }
                    }
                }
            }

            try {
                candidate.start(wait = false)
                ServerBinding(candidate, endpoint, qrPayloadJson)
            } catch (_: java.io.IOException) {
                null
            } catch (_: RuntimeException) {
                null
            }
        }

        if (boundServer == null) {
            failStartup("receiver-bind-failed")
            return
        }

        server = boundServer.server
        stateRepository.set(
            LanReceiveSessionState(
                status = LanReceiveStatus.LISTENING,
                endpoint = boundServer.endpoint,
                qrPayloadJson = boundServer.qrPayloadJson,
                expiresAt = sessionExpiresAt,
            ),
        )
        updateNotification(getString(R.string.lan_receive_notification_listening, boundServer.endpoint))
    }

    private suspend fun stopReceiver() {
        val currentServer = server ?: run {
            val currentStatus = stateRepository.state.value.status
            if (currentStatus != LanReceiveStatus.ERROR) {
                stateRepository.set(LanReceiveSessionState(status = LanReceiveStatus.STOPPED))
            }
            stopForeground(STOP_FOREGROUND_REMOVE)
            return
        }

        stateRepository.update {
            it.copy(status = LanReceiveStatus.STOPPING)
        }
        currentServer.stop(gracePeriodMillis = 1_000, timeoutMillis = 2_000)
        server = null
        stateRepository.set(LanReceiveSessionState(status = LanReceiveStatus.STOPPED))
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    private fun failStartup(errorCode: String) {
        stateRepository.set(
            LanReceiveSessionState(
                status = LanReceiveStatus.ERROR,
                errorMessage = errorCode,
            ),
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, buildNotification(contentText))
    }

    private fun buildNotification(contentText: String): Notification {
        createNotificationChannelIfNeeded()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_NAVIGATION_ROUTE, org.mozilla.tryfox.AppRoutes.RECEIVE_FROM_DESKTOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, TryFoxLanReceiveService::class.java).apply {
                action = ACTION_STOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(getString(R.string.lan_receive_notification_title))
            .setContentText(contentText)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(
                0,
                getString(R.string.lan_receive_stop_button),
                stopIntent,
            )
            .build()
    }

    private fun postReceivedMessageNotification(message: LanReceivedMessage) {
        createNotificationChannelIfNeeded()
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationId = MESSAGE_NOTIFICATION_BASE_ID + message.id.toInt().coerceAtLeast(1)
        notificationManager.notify(notificationId, buildReceivedMessageNotification(message))
        notificationManager.notify(MESSAGE_SUMMARY_NOTIFICATION_ID, buildMessageSummaryNotification())
    }

    private fun buildReceivedMessageNotification(message: LanReceivedMessage): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            message.id.toInt().coerceAtLeast(1),
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                val deepLink = message.tryfoxDeepLink
                if (!deepLink.isNullOrBlank()) {
                    action = Intent.ACTION_VIEW
                    data = deepLink.toUri()
                } else {
                    putExtra(EXTRA_NAVIGATION_ROUTE, org.mozilla.tryfox.AppRoutes.RECEIVE_MESSAGE_HISTORY)
                }
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(messageNotificationTitle(message))
            .setContentText(messageNotificationBody(message))
            .setStyle(NotificationCompat.BigTextStyle().bigText(messageNotificationBody(message)))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .setGroup(MESSAGE_NOTIFICATION_GROUP)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
    }

    private fun buildMessageSummaryNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            MESSAGE_SUMMARY_NOTIFICATION_ID,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(EXTRA_NAVIGATION_ROUTE, org.mozilla.tryfox.AppRoutes.RECEIVE_MESSAGE_HISTORY)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.lan_receive_message_group_title))
            .setContentText(getString(R.string.lan_receive_message_group_body))
            .setGroup(MESSAGE_NOTIFICATION_GROUP)
            .setGroupSummary(true)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()
    }

    private fun messageNotificationTitle(message: LanReceivedMessage): String =
        if (message.accepted) {
            when {
                !message.revision.isNullOrBlank() ->
                    getString(R.string.lan_receive_message_notification_title_revision, message.revision)
                !message.author.isNullOrBlank() ->
                    getString(R.string.lan_receive_message_notification_title_author, message.author)
                else -> getString(R.string.lan_receive_message_notification_title_generic)
            }
        } else {
            getString(R.string.lan_receive_message_notification_title_rejected)
        }

    private fun messageNotificationBody(message: LanReceivedMessage): String =
        if (message.accepted) {
            when {
                !message.sourceUrl.isNullOrBlank() ->
                    getString(R.string.lan_receive_message_notification_body_source, message.sourceUrl)
                !message.messageId.isNullOrBlank() ->
                    getString(R.string.lan_receive_message_notification_body_id, message.messageId)
                else -> getString(R.string.lan_receive_message_notification_body_generic)
            }
        } else {
            getString(
                R.string.lan_receive_message_notification_body_rejected,
                message.error ?: getString(R.string.common_unknown_error),
            )
        }

    private fun createNotificationChannelIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (notificationManager.getNotificationChannel(NOTIFICATION_CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.lan_receive_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        )
        notificationManager.createNotificationChannel(channel)
    }

    private data class ServerBinding(
        val server: io.ktor.server.engine.EmbeddedServer<*, *>,
        val endpoint: String,
        val qrPayloadJson: String,
    )

    companion object {
        private const val NOTIFICATION_CHANNEL_ID = "tryfox_lan_receive"
        private const val NOTIFICATION_ID = 7001
        private const val MESSAGE_SUMMARY_NOTIFICATION_ID = 7100
        private const val MESSAGE_NOTIFICATION_BASE_ID = 7200
        private const val MESSAGE_NOTIFICATION_GROUP = "tryfox_lan_messages"
        private const val DEFAULT_PORT = 8765
        private const val LAST_PORT = 8775
        private const val SESSION_DURATION_MS = 7 * 24 * 60 * 60 * 1000L
        private const val MESSAGE_PATH = "/tryfox/v1/messages"
        private const val QR_MODE = "tryfox-lan-receive"
        private const val ACTION_STOP = "org.mozilla.tryfox.lan.action.STOP"

        fun startIntent(context: Context): Intent =
            Intent(context, TryFoxLanReceiveService::class.java)

        fun stopIntent(context: Context): Intent =
            Intent(context, TryFoxLanReceiveService::class.java).apply {
                action = ACTION_STOP
            }
    }
}
