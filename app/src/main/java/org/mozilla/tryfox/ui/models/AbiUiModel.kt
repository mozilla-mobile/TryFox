package org.mozilla.tryfox.ui.models

import org.mozilla.tryfox.util.isUniversalAbi

data class AbiUiModel(
    val name: String?,
    val isSupported: Boolean,
) {
    val isUniversal: Boolean get() = isUniversalAbi(name)
}

/**
 * Picks the variant matching [preferredAbi], falling back to universal, then to anything the
 * device can install, then to whatever came first. Nightly builds ship no universal variant, so
 * an unmatched affinity still resolves to an installable APK.
 */
fun List<ApkUiModel>.preferredAbiApk(preferredAbi: String): ApkUiModel? =
    firstOrNull { preferredAbi.equals(it.abi.name, ignoreCase = true) }
        ?: firstOrNull { it.abi.isUniversal }
        ?: firstOrNull { it.abi.isSupported }
        ?: firstOrNull()
