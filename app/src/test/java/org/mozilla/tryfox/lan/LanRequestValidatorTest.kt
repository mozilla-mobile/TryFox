package org.mozilla.tryfox.lan

import io.ktor.http.HttpStatusCode
import kotlinx.serialization.encodeToString
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class LanRequestValidatorTest {
    private val currentTimeMillis = 1_760_000_000_000L
    private val identity = LanReceiveIdentity(
        deviceId = "device-123",
        deviceName = "Pixel Test",
        sharedSecret = ByteArray(32) { index -> index.toByte() }.toBase64Url(),
    )

    @Test
    fun `accepts valid signed request`() {
        val request = request(messageId = "message-1")
        val bodyBytes = LanJson.encodeToString(request).toByteArray()
        val validator = validator()

        val result = validator.validate(
            headers = signedHeaders(bodyBytes = bodyBytes, nonce = "nonce-1"),
            bodyBytes = bodyBytes,
        )

        val success = assertInstanceOf(LanValidationResult.Success::class.java, result)
        assertEquals("message-1", success.message.messageId)
        assertEquals("extension-id", success.extensionId)
    }

    @Test
    fun `rejects stale timestamp`() {
        val request = request(messageId = "message-1")
        val bodyBytes = LanJson.encodeToString(request).toByteArray()
        val validator = validator()

        val result = validator.validate(
            headers = signedHeaders(
                bodyBytes = bodyBytes,
                nonce = "nonce-1",
                timestamp = currentTimeMillis - 10 * 60 * 1000L,
            ),
            bodyBytes = bodyBytes,
        )

        val failure = assertInstanceOf(LanValidationResult.Failure::class.java, result)
        assertEquals(HttpStatusCode.Unauthorized, failure.statusCode)
        assertEquals("stale-timestamp", failure.errorCode)
    }

    @Test
    fun `rejects replayed nonce`() {
        val request = request(messageId = "message-1")
        val bodyBytes = LanJson.encodeToString(request).toByteArray()
        val validator = validator()
        val headers = signedHeaders(bodyBytes = bodyBytes, nonce = "nonce-1")

        val first = validator.validate(headers = headers, bodyBytes = bodyBytes)
        val second = validator.validate(headers = headers, bodyBytes = bodyBytes)

        assertInstanceOf(LanValidationResult.Success::class.java, first)
        val failure = assertInstanceOf(LanValidationResult.Failure::class.java, second)
        assertEquals(HttpStatusCode.Conflict, failure.statusCode)
        assertEquals("replayed-nonce", failure.errorCode)
    }

    @Test
    fun `rejects duplicate message id`() {
        val validator = validator()
        val firstRequest = request(messageId = "message-1")
        val firstBody = LanJson.encodeToString(firstRequest).toByteArray()
        val secondRequest = request(messageId = "message-1")
        val secondBody = LanJson.encodeToString(secondRequest).toByteArray()

        val first = validator.validate(
            headers = signedHeaders(bodyBytes = firstBody, nonce = "nonce-1"),
            bodyBytes = firstBody,
        )
        val second = validator.validate(
            headers = signedHeaders(bodyBytes = secondBody, nonce = "nonce-2"),
            bodyBytes = secondBody,
        )

        assertInstanceOf(LanValidationResult.Success::class.java, first)
        val failure = assertInstanceOf(LanValidationResult.Failure::class.java, second)
        assertEquals(HttpStatusCode.Conflict, failure.statusCode)
        assertEquals("duplicate-message-id", failure.errorCode)
    }

    @Test
    fun `rejects request without revision or author`() {
        val request = request(messageId = "message-1", revision = null, author = null)
        val bodyBytes = LanJson.encodeToString(request).toByteArray()
        val validator = validator()

        val result = validator.validate(
            headers = signedHeaders(bodyBytes = bodyBytes, nonce = "nonce-1"),
            bodyBytes = bodyBytes,
        )

        val failure = assertInstanceOf(LanValidationResult.Failure::class.java, result)
        assertEquals(HttpStatusCode.BadRequest, failure.statusCode)
        assertEquals("missing-target", failure.errorCode)
    }

    private fun validator() = LanRequestValidator(
        identity = identity,
        sessionExpiresAt = currentTimeMillis + 60_000L,
        currentTimeMillisProvider = { currentTimeMillis },
        replayCache = ExpiringReplayCache(currentTimeMillisProvider = { currentTimeMillis }),
        messageIdCache = ExpiringReplayCache(currentTimeMillisProvider = { currentTimeMillis }),
    )

    private fun request(
        messageId: String,
        revision: String? = "abcdef123456",
        author: String? = null,
    ) = LanIncomingMessage(
        version = 1,
        type = "try-revision",
        messageId = messageId,
        sentAt = currentTimeMillis,
        sourceUrl = "https://treeherder.mozilla.org/jobs?repo=try&revision=abcdef123456",
        tryfoxDeepLink = "tryfox://jobs?repo=try&revision=abcdef123456",
        repo = "try",
        revision = revision,
        author = author,
    )

    private fun signedHeaders(
        bodyBytes: ByteArray,
        nonce: String,
        timestamp: Long = currentTimeMillis,
    ): Map<String, String> {
        val signature = hmacSha256Base64Url(
            secret = identity.sharedSecret.decodeBase64Url(),
            data = listOf(
                "TRYFOX-LAN-V1",
                "POST",
                "/tryfox/v1/messages",
                identity.deviceId,
                "extension-id",
                timestamp.toString(),
                nonce,
                sha256Base64Url(bodyBytes),
            ).joinToString("\n"),
        )

        return mapOf(
            "X-Tryfox-Device-Id" to identity.deviceId,
            "X-Tryfox-Extension-Id" to "extension-id",
            "X-Tryfox-Timestamp" to timestamp.toString(),
            "X-Tryfox-Nonce" to nonce,
            "X-Tryfox-Signature" to signature,
        )
    }
}
