package org.mozilla.fenixinstaller.ui.models

sealed class FocusApksState {
    object Loading : FocusApksState()
    data class Success(val apks: List<ApkUiModel>) : FocusApksState()
    data class Error(val message: String?) : FocusApksState()

}