package org.mozilla.tryfox.lan

import android.os.Build
import kotlinx.serialization.json.Json
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

private const val HMAC_ALGORITHM = "HmacSHA256"
private const val SHA_256 = "SHA-256"

fun ByteArray.toBase64Url(): String =
    Base64.getUrlEncoder().withoutPadding().encodeToString(this)

fun String.decodeBase64Url(): ByteArray =
    Base64.getUrlDecoder().decode(this)

fun randomBase64Url(byteCount: Int, secureRandom: SecureRandom = SecureRandom()): String {
    val bytes = ByteArray(byteCount)
    secureRandom.nextBytes(bytes)
    return bytes.toBase64Url()
}

fun sha256Base64Url(bytes: ByteArray): String = MessageDigest.getInstance(SHA_256).digest(bytes).toBase64Url()

fun hmacSha256Base64Url(secret: ByteArray, data: String): String {
    val mac = Mac.getInstance(HMAC_ALGORITHM)
    mac.init(SecretKeySpec(secret, HMAC_ALGORITHM))
    return mac.doFinal(data.toByteArray(Charsets.UTF_8)).toBase64Url()
}

fun constantTimeEquals(left: String, right: String): Boolean =
    MessageDigest.isEqual(left.toByteArray(Charsets.UTF_8), right.toByteArray(Charsets.UTF_8))

fun defaultDeviceName(): String {
    val manufacturer = Build.MANUFACTURER?.trim().orEmpty()
    val model = Build.MODEL?.trim().orEmpty()
    return when {
        manufacturer.isBlank() && model.isBlank() -> "Android device"
        model.startsWith(manufacturer, ignoreCase = true) -> model
        manufacturer.isBlank() -> model
        model.isBlank() -> manufacturer
        else -> "$manufacturer $model"
    }
}

val LanJson: Json = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
}
