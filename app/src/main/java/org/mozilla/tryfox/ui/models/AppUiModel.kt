package org.mozilla.tryfox.ui.models

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.util.Version

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

val AppUiModel.newVersionAvailable: Boolean
    get() {
        val latestApkVersionString = (apks as? ApksResult.Success)?.apks?.firstOrNull()?.version ?: return false
        val installedVersionString = installedVersion ?: return true

        val latestVersion = Version.from(latestApkVersionString) ?: return false
        val installedVersion = Version.from(installedVersionString) ?: return false

        return latestVersion > installedVersion
    }
