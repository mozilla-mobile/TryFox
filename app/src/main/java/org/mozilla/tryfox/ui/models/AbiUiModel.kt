package org.mozilla.tryfox.ui.models

import org.mozilla.tryfox.util.isUniversalAbi

data class AbiUiModel(
    val name: String?,
    val isSupported: Boolean,
) {
    val isUniversal: Boolean get() = isUniversalAbi(name)
}
