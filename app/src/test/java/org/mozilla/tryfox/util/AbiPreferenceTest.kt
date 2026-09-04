package org.mozilla.tryfox.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ApkUiModel
import org.mozilla.tryfox.ui.models.preferredAbiApk
import java.io.File

class AbiPreferenceTest {

    private val deviceAbis = listOf("arm64-v8a", "armeabi-v7a", "armeabi")

    @Test
    fun `universal installs on any device and unknown ABIs do not`() {
        assertTrue(isAbiSupported(UNIVERSAL_ABI, deviceAbis))
        assertTrue(isAbiSupported("Universal", deviceAbis))
        assertTrue(isAbiSupported("arm64-v8a", deviceAbis))
        assertFalse(isAbiSupported("x86_64", deviceAbis))
        assertFalse(isAbiSupported(null, deviceAbis))
    }

    @Test
    fun `options list universal first then every buildable device ABI`() {
        // The archive publishes arm64-v8a, armeabi-v7a, x86_64 and universal; legacy armeabi is
        // still advertised by the platform but nothing targets it, so it is not offered.
        assertEquals(listOf(UNIVERSAL_ABI, "arm64-v8a", "armeabi-v7a"), abiPreferenceOptions(deviceAbis))
        assertEquals(listOf(UNIVERSAL_ABI, "x86_64", "x86"), abiPreferenceOptions(listOf("x86_64", "x86")))
        assertEquals(listOf(UNIVERSAL_ABI), abiPreferenceOptions(emptyList()))
        assertEquals(listOf(UNIVERSAL_ABI), abiPreferenceOptions(listOf("armeabi", "mips64")))
        assertEquals(listOf(UNIVERSAL_ABI, "arm64-v8a"), abiPreferenceOptions(listOf("arm64-v8a", "arm64-v8a")))
    }

    @Test
    fun `stored affinity falls back to universal when blank or foreign to this device`() {
        assertEquals("arm64-v8a", resolvePreferredAbi("arm64-v8a", deviceAbis))
        assertEquals(UNIVERSAL_ABI, resolvePreferredAbi(UNIVERSAL_ABI, deviceAbis))
        assertEquals(UNIVERSAL_ABI, resolvePreferredAbi(null, deviceAbis))
        assertEquals(UNIVERSAL_ABI, resolvePreferredAbi("", deviceAbis))
        // Recorded on an x86 emulator, then restored onto an ARM device.
        assertEquals(UNIVERSAL_ABI, resolvePreferredAbi("x86_64", deviceAbis))
        // The device runs armeabi, but it is no longer an option, so it resolves back to universal.
        assertEquals(UNIVERSAL_ABI, resolvePreferredAbi("armeabi", deviceAbis))
    }

    @Test
    fun `installed ABI is read from the native library directory`() {
        val installDir = "/data/app/~~xY==/org.mozilla.firefox-aB=="
        assertEquals("arm64-v8a", abiFromNativeLibraryDir("$installDir/lib/arm64"))
        assertEquals("armeabi-v7a", abiFromNativeLibraryDir("$installDir/lib/arm"))
        assertEquals("x86_64", abiFromNativeLibraryDir("$installDir/lib/x86_64"))
        assertEquals("x86", abiFromNativeLibraryDir("$installDir/lib/x86/"))
        // An unrecognised directory is reported as-is rather than dropped.
        assertEquals("riscv64", abiFromNativeLibraryDir("$installDir/lib/riscv64"))
        assertNull(abiFromNativeLibraryDir(null))
        assertNull(abiFromNativeLibraryDir(""))
        // A package with no native code has no architecture subdirectory, so no active ABI.
        assertNull(abiFromNativeLibraryDir("$installDir/lib"))
    }

    @Test
    fun `preferred variant wins and universal is the fallback`() {
        val releaseApks = listOf(apk("arm64-v8a"), apk("armeabi-v7a"), apk(UNIVERSAL_ABI))

        assertEquals(UNIVERSAL_ABI, releaseApks.preferredAbiApk(UNIVERSAL_ABI)?.abi?.name)
        assertEquals("arm64-v8a", releaseApks.preferredAbiApk("arm64-v8a")?.abi?.name)
        // x86_64 has no variant here, so the universal APK still installs.
        assertEquals(UNIVERSAL_ABI, releaseApks.preferredAbiApk("x86_64")?.abi?.name)
    }

    @Test
    fun `nightly builds without a universal variant fall back to a supported ABI`() {
        val nightlyApks = listOf(
            apk("x86_64", isSupported = false),
            apk("arm64-v8a"),
        )

        assertEquals("arm64-v8a", nightlyApks.preferredAbiApk(UNIVERSAL_ABI)?.abi?.name)
        assertEquals("arm64-v8a", nightlyApks.preferredAbiApk("arm64-v8a")?.abi?.name)
        assertEquals("x86_64", nightlyApks.preferredAbiApk("x86_64")?.abi?.name)
        assertNull(emptyList<ApkUiModel>().preferredAbiApk(UNIVERSAL_ABI))
    }

    private fun apk(abiName: String, isSupported: Boolean = true) = ApkUiModel(
        originalString = "fenix-153.0-android-$abiName/",
        date = "",
        appName = FENIX_RELEASE,
        version = "153.0",
        abi = AbiUiModel(abiName, isSupported),
        url = "https://archive.invalid/fenix-153.0.multi.android-$abiName.apk",
        fileName = "fenix-153.0.multi.android-$abiName.apk",
        downloadState = DownloadState.NotDownloaded,
        uniqueKey = "$FENIX_RELEASE/fenix-153.0.multi.android-$abiName.apk",
        apkDir = File("."),
    )
}
