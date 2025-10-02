package org.mozilla.tryfox.ui.models

import org.mozilla.tryfox.model.AppState

sealed class ApksState {
    object Loading : ApksState()

    data class Success(
        val apks: List<ApkUiModel>,
        val appState: AppState?,
    ) : ApksState()

    data class Error(
        val message: String?,
        val appState: AppState?,
    ) : ApksState()
}
