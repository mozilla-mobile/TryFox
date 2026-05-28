package org.mozilla.tryfox.lan

import kotlinx.serialization.Serializable

@Serializable
data class LanReceiveIdentity(
    val deviceId: String,
    val deviceName: String,
    val sharedSecret: String,
)

@Serializable
data class LanQrPayload(
    val version: Int,
    val mode: String,
    val deviceId: String,
    val deviceName: String,
    val endpoint: String,
    val sharedSecret: String,
    val expiresAt: Long,
)

@Serializable
data class LanIncomingMessage(
    val version: Int,
    val type: String,
    val messageId: String,
    val sentAt: Long,
    val sourceUrl: String,
    val tryfoxDeepLink: String,
    val repo: String? = null,
    val revision: String? = null,
    val author: String? = null,
)

@Serializable
data class LanReceiveSuccessResponse(
    val ok: Boolean,
    val messageId: String,
)

@Serializable
data class LanReceiveErrorResponse(
    val ok: Boolean,
    val error: String,
)

enum class LanReceiveStatus {
    STOPPED,
    STARTING,
    LISTENING,
    STOPPING,
    ERROR,
}

data class LanReceivedMessage(
    val id: Long = 0L,
    val receivedAt: Long,
    val accepted: Boolean,
    val error: String? = null,
    val messageId: String? = null,
    val extensionId: String? = null,
    val sourceUrl: String? = null,
    val tryfoxDeepLink: String? = null,
    val repo: String? = null,
    val revision: String? = null,
    val author: String? = null,
    val bodyHash: String? = null,
)

data class LanReceiveSessionState(
    val status: LanReceiveStatus = LanReceiveStatus.STOPPED,
    val endpoint: String? = null,
    val qrPayloadJson: String? = null,
    val expiresAt: Long? = null,
    val lastReceivedMessage: LanReceivedMessage? = null,
    val errorMessage: String? = null,
)
