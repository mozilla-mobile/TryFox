package org.mozilla.tryfox.ui.models

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.data.InstalledTryBuild
import org.mozilla.tryfox.util.Version

sealed class ApksResult {
    data object Loading : ApksResult()

    /**
     * A build's APKs, one per ABI in archive order, and the one the card offers, chosen by the view
     * model. [selectedApkKey] is null when none suits this device.
     */
    data class Success(val apks: List<ApkUiModel>, val selectedApkKey: String?) : ApksResult() {
        val selectedApk: ApkUiModel? get() = apks.firstOrNull { it.uniqueKey == selectedApkKey }
    }
    data class Error(val message: String) : ApksResult()
}

/**
 * One selectable Nightly build for a picked date. A single calendar day can have several builds
 * (one per push); each is identified by its full timestamp [id] and shown by time of day.
 */
data class NightlyBuildOption(
    val id: String, // the build's full "yyyy-MM-dd-HH-mm-ss" timestamp; groups its ABI variants
    val label: String, // date + time for display, e.g. "2026-07-24 09:17"
)

/**
 * Release-candidate state for the version picker. The `candidates/` listing ([baseVersions]) is
 * fetched with the home load so majors with only candidate builds can be selected (the picker
 * retries if that failed); RC build numbers are fetched per major as the user browses to it and
 * merged into [AppUiModel.availableReleaseVersions].
 */
data class ReleaseCandidatesUiState(
    val baseVersions: List<String> = emptyList(),
    val isBaseVersionsLoaded: Boolean = false,
    val loadedMajors: Set<Int> = emptySet(),
    val loadingMajors: Set<Int> = emptySet(),
)

data class AppUiModel(
    val name: String,
    val packageName: String,
    val installedVersion: String?,
    val installedVersionCode: Long? = null,
    val installedDate: String?,
    val installingPackageName: String? = null,
    val splitNames: List<String> = emptyList(),
    val activeAbi: String? = null,
    val installedTryBuild: InstalledTryBuild? = null,
    val apks: ApksResult,
    val userPickedDate: LocalDate? = null,
    val selectedReleaseVersion: String? = null,
    val availableReleaseVersions: List<String> = emptyList(),
    // RC builds are loaded lazily by the version picker; only the candidates listing comes with the home load.
    val releaseCandidates: ReleaseCandidatesUiState = ReleaseCandidatesUiState(),
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
