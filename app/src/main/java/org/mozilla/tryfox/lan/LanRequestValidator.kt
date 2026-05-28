package org.mozilla.tryfox.lan

import io.ktor.http.HttpStatusCode

private const val DEVICE_ID_HEADER = "X-Tryfox-Device-Id"
private const val EXTENSION_ID_HEADER = "X-Tryfox-Extension-Id"
private const val TIMESTAMP_HEADER = "X-Tryfox-Timestamp"
private const val NONCE_HEADER = "X-Tryfox-Nonce"
private const val SIGNATURE_HEADER = "X-Tryfox-Signature"
private const val SIGNING_PREFIX = "TRYFOX-LAN-V1"
private const val MESSAGE_PATH = "/tryfox/v1/messages"

class LanRequestValidator(
    private val identity: LanReceiveIdentity,
    private val sessionExpiresAt: Long,
    private val currentTimeMillisProvider: () -> Long = System::currentTimeMillis,
    private val replayCache: ExpiringReplayCache = ExpiringReplayCache(),
    private val messageIdCache: ExpiringReplayCache = ExpiringReplayCache(),
) {
    fun validate(
        headers: Map<String, String>,
        bodyBytes: ByteArray,
    ): LanValidationResult {
        if (currentTimeMillisProvider() > sessionExpiresAt) {
            return LanValidationResult.Failure(HttpStatusCode.Gone, "session-expired")
        }

        val deviceId = headers[DEVICE_ID_HEADER]?.takeIf { it.isNotBlank() }
            ?: return LanValidationResult.Failure(HttpStatusCode.BadRequest, "missing-device-id")
        val extensionId = headers[EXTENSION_ID_HEADER]?.takeIf { it.isNotBlank() }
            ?: return LanValidationResult.Failure(HttpStatusCode.BadRequest, "missing-extension-id")
        val timestampValue = headers[TIMESTAMP_HEADER]?.takeIf { it.isNotBlank() }
            ?: return LanValidationResult.Failure(HttpStatusCode.BadRequest, "missing-timestamp")
        val nonce = headers[NONCE_HEADER]?.takeIf { it.isNotBlank() }
            ?: return LanValidationResult.Failure(HttpStatusCode.BadRequest, "missing-nonce")
        val signature = headers[SIGNATURE_HEADER]?.takeIf { it.isNotBlank() }
            ?: return LanValidationResult.Failure(HttpStatusCode.BadRequest, "missing-signature")

        if (deviceId != identity.deviceId) {
            return LanValidationResult.Failure(HttpStatusCode.Unauthorized, "invalid-device-id")
        }

        val timestamp = timestampValue.toLongOrNull()
            ?: return LanValidationResult.Failure(HttpStatusCode.BadRequest, "invalid-timestamp")
        val now = currentTimeMillisProvider()
        if (kotlin.math.abs(now - timestamp) > TIMESTAMP_TOLERANCE_MS) {
            return LanValidationResult.Failure(HttpStatusCode.Unauthorized, "stale-timestamp")
        }

        val nonceKey = "$extensionId:$deviceId:$nonce"
        if (!replayCache.tryStore(nonceKey, timestamp + TIMESTAMP_TOLERANCE_MS)) {
            return LanValidationResult.Failure(HttpStatusCode.Conflict, "replayed-nonce")
        }

        val bodyHash = sha256Base64Url(bodyBytes)
        val signingString = listOf(
            SIGNING_PREFIX,
            "POST",
            MESSAGE_PATH,
            deviceId,
            extensionId,
            timestampValue,
            nonce,
            bodyHash,
        ).joinToString(separator = "\n")
        val expectedSignature = hmacSha256Base64Url(identity.sharedSecret.decodeBase64Url(), signingString)
        if (!constantTimeEquals(expectedSignature, signature)) {
            return LanValidationResult.Failure(HttpStatusCode.Unauthorized, "invalid-signature")
        }

        val message = runCatching {
            LanJson.decodeFromString<org.mozilla.tryfox.lan.LanIncomingMessage>(bodyBytes.decodeToString())
        }.getOrElse {
            return LanValidationResult.Failure(HttpStatusCode.BadRequest, "invalid-json")
        }

        if (message.version != 1 || message.type != "try-revision") {
            return LanValidationResult.Failure(HttpStatusCode.BadRequest, "unsupported-payload")
        }
        if (message.revision.isNullOrBlank() && message.author.isNullOrBlank()) {
            return LanValidationResult.Failure(HttpStatusCode.BadRequest, "missing-target")
        }
        if (!messageIdCache.tryStore(message.messageId, sessionExpiresAt)) {
            return LanValidationResult.Failure(HttpStatusCode.Conflict, "duplicate-message-id")
        }

        return LanValidationResult.Success(
            message = message,
            extensionId = extensionId,
            bodyHash = bodyHash,
        )
    }

    private companion object {
        const val TIMESTAMP_TOLERANCE_MS = 5 * 60 * 1000L
    }
}

sealed interface LanValidationResult {
    data class Success(
        val message: LanIncomingMessage,
        val extensionId: String,
        val bodyHash: String,
    ) : LanValidationResult

    data class Failure(
        val statusCode: HttpStatusCode,
        val errorCode: String,
    ) : LanValidationResult
}

class ExpiringReplayCache(
    private val currentTimeMillisProvider: () -> Long = System::currentTimeMillis,
) {
    private val entries = linkedMapOf<String, Long>()

    @Synchronized
    fun tryStore(key: String, expiresAt: Long): Boolean {
        cleanup()
        if (entries.containsKey(key)) {
            return false
        }
        entries[key] = expiresAt
        return true
    }

    @Synchronized
    private fun cleanup() {
        val now = currentTimeMillisProvider()
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            if (iterator.next().value <= now) {
                iterator.remove()
            }
        }
    }
}
