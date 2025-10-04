package org.mozilla.tryfox.ui.models

import kotlinx.datetime.LocalDate

sealed class ApksResult {
    data object Loading : ApksResult()
    data class Success(val apks: List<ApkUiModel>) : ApksResult()
    data class Error(val message: String) : ApksResult()
}

data class AppUiModel(
    val name: String,
    val packageName: String,
    val installedVersion: String?,
    val installedDate: String?,
    val apks: ApksResult,
    val userPickedDate: LocalDate? = null,
)
