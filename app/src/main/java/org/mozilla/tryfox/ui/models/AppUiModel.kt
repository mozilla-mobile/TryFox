package org.mozilla.tryfox.ui.models

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.data.InstalledTryBuild
import org.mozilla.tryfox.util.Version

sealed class ApksResult {
    data object Loading : ApksResult()
    data class Success(val apks: List<ApkUiModel>) : ApksResult()
    data class Error(val message: String) : ApksResult()
}

/**
 * One selectable Nightly build for a picked date. A single calendar day can have several builds
 * (one per push); each is identified by its full timestamp [id] and shown by time of day.
 */
data class NightlyBuildOption(
    val id: String, // the build's full "yyyy-MM-dd-HH-mm-ss" timestamp; groups its ABI variants
    val label: String, // full date + time for display, e.g. "2026-07-24 09:17:32"
)

data class AppUiModel(
    val name: String,
    val packageName: String,
    val installedVersion: String?,
    val installedVersionCode: Long? = null,
    val installedDate: String?,
    val installingPackageName: String? = null,
    val splitNames: List<String> = emptyList(),
    val installedTryBuild: InstalledTryBuild? = null,
    val apks: ApksResult,
    val userPickedDate: LocalDate? = null,
    val selectedReleaseVersion: String? = null,
    val availableReleaseVersions: List<String> = emptyList(),
    // When a picked date has multiple builds, these drive a one-shot picker prompt. Empty otherwise.
    val pendingBuildOptions: List<NightlyBuildOption> = emptyList(),
)

val AppUiModel.newVersionAvailable: Boolean
    get() {
        val latestApkVersionString = (apks as? ApksResult.Success)?.apks?.firstOrNull()?.version ?: return false
        val installedVersionString = installedVersion ?: return true

        val latestVersion = Version.from(latestApkVersionString) ?: return false
        val installedVersion = Version.from(installedVersionString) ?: return false

        return latestVersion > installedVersion
    }
