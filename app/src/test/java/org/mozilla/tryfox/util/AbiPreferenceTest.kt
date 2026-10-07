package org.mozilla.tryfox.util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mozilla.tryfox.data.DownloadState
import org.mozilla.tryfox.ui.models.AbiUiModel
import org.mozilla.tryfox.ui.models.ApkUiModel
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
    fun `preference order is the resolved preference, then universal, then device ABIs in device order`() {
        val emulatorAbis = listOf("x86_64", "arm64-v8a")

        assertEquals(listOf(UNIVERSAL_ABI, "x86_64", "arm64-v8a"), abiPreferenceOrder(UNIVERSAL_ABI, emulatorAbis))
        assertEquals(listOf("arm64-v8a", UNIVERSAL_ABI, "x86_64"), abiPreferenceOrder("arm64-v8a", emulatorAbis))
        // Unset, the device's primary ABI comes first.
        assertEquals(listOf("x86_64", UNIVERSAL_ABI, "arm64-v8a"), abiPreferenceOrder(null, emulatorAbis))
        // ABIs the device can't run, and retired ones, are never offered.
        assertEquals(listOf("arm64-v8a", UNIVERSAL_ABI), abiPreferenceOrder(null, listOf("arm64-v8a", "armeabi")))
    }

    @Test
    fun `stored affinity falls back to the device's primary ABI when unset or foreign to this device`() {
        assertEquals("armeabi-v7a", resolvePreferredAbi("armeabi-v7a", deviceAbis))
        // An explicit universal choice is kept.
        assertEquals(UNIVERSAL_ABI, resolvePreferredAbi(UNIVERSAL_ABI, deviceAbis))
        assertEquals("arm64-v8a", resolvePreferredAbi(null, deviceAbis))
        assertEquals("arm64-v8a", resolvePreferredAbi("", deviceAbis))
        // Recorded on an x86 emulator, then restored onto an ARM device.
        assertEquals("arm64-v8a", resolvePreferredAbi("x86_64", deviceAbis))
        // The device runs armeabi, but it is no longer an option, so it resolves to the default.
        assertEquals("arm64-v8a", resolvePreferredAbi("armeabi", deviceAbis))
    }

    @Test
    fun `unset preference resolves to the device's first usable ABI`() {
        assertEquals("x86_64", resolvePreferredAbi(null, listOf("x86_64", "arm64-v8a")))
        // Nothing buildable is reported, so fall back to universal, which installs anywhere.
        assertEquals(UNIVERSAL_ABI, resolvePreferredAbi(null, listOf("armeabi")))
        assertEquals(UNIVERSAL_ABI, resolvePreferredAbi(null, emptyList()))
    }

    @Test
    fun `cascade builds each list from the one before`() {
        // An x86_64 emulator that also runs ARM code, and advertises a retired ABI.
        val emulatorAbis = listOf("x86_64", "arm64-v8a", "armeabi")

        assertEquals(listOf("x86_64", "arm64-v8a"), deviceAbiOptions(emulatorAbis))
        assertEquals(listOf(UNIVERSAL_ABI, "x86_64", "arm64-v8a"), abiPreferenceOptions(emulatorAbis))

        // Nothing stored: the device's primary ABI leads, then universal, then the rest.
        assertEquals("x86_64", resolvePreferredAbi(null, emulatorAbis))
        assertEquals(listOf("x86_64", UNIVERSAL_ABI, "arm64-v8a"), abiPreferenceOrder(null, emulatorAbis))

        // An explicit choice leads instead, without repeating it later in the order.
        assertEquals("arm64-v8a", resolvePreferredAbi("arm64-v8a", emulatorAbis))
        assertEquals(listOf("arm64-v8a", UNIVERSAL_ABI, "x86_64"), abiPreferenceOrder("arm64-v8a", emulatorAbis))
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
        val releaseAbis = listOf("arm64-v8a", "armeabi-v7a", UNIVERSAL_ABI)

        assertEquals(UNIVERSAL_ABI, releaseAbis.pickedFor(UNIVERSAL_ABI))
        assertEquals("arm64-v8a", releaseAbis.pickedFor("arm64-v8a"))
        // x86_64 isn't an option on this device, so the device's primary ABI is used instead.
        assertEquals("arm64-v8a", releaseAbis.pickedFor("x86_64"))
        // A build without the preferred variant falls back to universal.
        assertEquals(UNIVERSAL_ABI, listOf("armeabi-v7a", UNIVERSAL_ABI).pickedFor("arm64-v8a"))
    }

    @Test
    fun `builds without a universal variant fall back to a supported ABI`() {
        // e.g. a Focus build, whose universal APK isn't published.
        val abis = listOf("x86_64", "arm64-v8a")

        assertEquals("arm64-v8a", abis.pickedFor(UNIVERSAL_ABI))
        assertEquals("arm64-v8a", abis.pickedFor("arm64-v8a"))
        // The x86_64 APK can't run on this ARM device, so it's never picked.
        assertEquals("arm64-v8a", abis.pickedFor("x86_64"))
        assertNull(listOf("x86_64").pickedFor(UNIVERSAL_ABI))
    }

    /** The ABI from this build that [abiPreferenceOrder] picks first, as the home card does. */
    private fun List<String>.pickedFor(preferredAbi: String): String? =
        abiPreferenceOrder(preferredAbi, deviceAbis).firstOrNull { it in this }

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
