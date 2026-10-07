package org.mozilla.tryfox.util

// Retired with NDK r17; the platform still advertises these so pre-ARMv7 APKs keep working, but
// nothing is built for them any more, so offering them would only ever fall back to universal.
private val LEGACY_ABIS = setOf("armeabi", "mips", "mips64")

/** The ABI name the archive uses for the APK that bundles every architecture. */
const val UNIVERSAL_ABI = "universal"

fun isUniversalAbi(abiName: String?): Boolean = UNIVERSAL_ABI.equals(abiName, ignoreCase = true)

/** True when an APK built for [abiName] can be installed on a device running [deviceAbis]. */
fun isAbiSupported(abiName: String?, deviceAbis: List<String>): Boolean =
    isUniversalAbi(abiName) || deviceAbis.any { it.equals(abiName, ignoreCase = true) }

// Which APK to offer is decided by a cascade, each step building on the one before:
//   deviceAbiOptions     -> the device's ABIs that APKs are built for
//   abiPreferenceOptions -> those plus universal: the choices offered in Settings
//   resolvePreferredAbi  -> the stored choice if it's an option, else the device's primary ABI
//   abiPreferenceOrder   -> that choice, then universal, then the device's other ABIs

/** Every ABI the device runs that an APK could plausibly be built for, in the device's order. */
fun deviceAbiOptions(deviceAbis: List<String>): List<String> =
    deviceAbis.filterNot { isUniversalAbi(it) || it.lowercase() in LEGACY_ABIS }.distinct()

/** The affinity options offered in Settings: universal first, then [deviceAbiOptions]. */
fun abiPreferenceOptions(deviceAbis: List<String>): List<String> = listOf(UNIVERSAL_ABI) + deviceAbiOptions(deviceAbis)

/**
 * The stored [preferredAbi] if it's still an option on this device; otherwise the device's primary
 * ABI, or universal if the device reports none we build for. This favors smaller downloads over
 * universal APKs unless the user explicitly picks universal.
 */
fun resolvePreferredAbi(preferredAbi: String?, deviceAbis: List<String>): String {
    val options = abiPreferenceOptions(deviceAbis)
    return preferredAbi
        ?.takeIf { options.any { option -> option.equals(it, ignoreCase = true) } }
        ?: deviceAbiOptions(deviceAbis).firstOrNull()
        ?: UNIVERSAL_ABI
}

/**
 * The ABIs a build's APK is picked from, best first: the resolved preference, then universal (it
 * installs anywhere), then the device's other ABIs in its own order. ABIs the device can't run are
 * never offered.
 */
fun abiPreferenceOrder(preferredAbi: String?, deviceAbis: List<String>): List<String> =
    (listOf(resolvePreferredAbi(preferredAbi, deviceAbis), UNIVERSAL_ABI) + deviceAbiOptions(deviceAbis)).distinct()

/**
 * Determine the active ABI for installed package from `ApplicationInfo.nativeLibraryDir` path.
 *
 * This corresponds to what ABI was chosen by package manager when the package was installed. The
 * package itself may have included multiple ABIs, but installer selected a single one that the
 * application will execute under.
 */
fun abiFromNativeLibraryDir(nativeLibraryDir: String?): String? {
    // The library names don't always match ABI so patch up the ones we know.
    val directoryAbis = mapOf(
        "arm64" to "arm64-v8a",
        "arm" to "armeabi-v7a",
        "x86_64" to "x86_64",
        "x86" to "x86",
    )
    return nativeLibraryDir
        ?.trimEnd('/')
        ?.substringAfterLast('/')
        ?.lowercase()
        // A package with no native code stops at the "lib" root, with no architecture below it.
        ?.takeIf { it.isNotBlank() && it != "lib" }
        ?.let { directory -> directoryAbis[directory] ?: directory }
}
