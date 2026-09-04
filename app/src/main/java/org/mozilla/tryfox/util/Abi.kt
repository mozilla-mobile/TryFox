package org.mozilla.tryfox.util

// Retired with NDK r17; the platform still advertises these so pre-ARMv7 APKs keep working, but
// nothing is built for them any more, so offering them would only ever fall back to universal.
private val LEGACY_ABIS = setOf("armeabi", "mips", "mips64")

/** The ABI name the archive uses for the APK that bundles every architecture. */
const val UNIVERSAL_ABI = "universal"

/** The ABI affinity used until the user picks one: universal installs on any device. */
const val DEFAULT_PREFERRED_ABI = UNIVERSAL_ABI

fun isUniversalAbi(abiName: String?): Boolean = UNIVERSAL_ABI.equals(abiName, ignoreCase = true)

/** True when an APK built for [abiName] can be installed on a device running [deviceAbis]. */
fun isAbiSupported(abiName: String?, deviceAbis: List<String>): Boolean =
    isUniversalAbi(abiName) || deviceAbis.any { it.equals(abiName, ignoreCase = true) }

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

/**
 * The affinity options offered in Settings: universal first, then every ABI the device runs that
 * an APK could plausibly be built for.
 */
fun abiPreferenceOptions(deviceAbis: List<String>): List<String> {
    val filteredAbis = deviceAbis
        .filterNot { isUniversalAbi(it) || it.lowercase() in LEGACY_ABIS }
        .distinct()
    return listOf(UNIVERSAL_ABI) + filteredAbis
}

/** Falls back to universal when the stored ABI is blank or no longer supported by this device. */
fun resolvePreferredAbi(preferredAbi: String?, deviceAbis: List<String>): String =
    preferredAbi
        ?.takeIf { it.isNotBlank() }
        ?.takeIf { abiPreferenceOptions(deviceAbis).any { option -> option.equals(it, ignoreCase = true) } }
        ?: DEFAULT_PREFERRED_ABI
