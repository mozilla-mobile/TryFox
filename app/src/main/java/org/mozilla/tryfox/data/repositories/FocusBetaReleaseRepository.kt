package org.mozilla.tryfox.data.repositories

import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.util.FOCUS_BETA

/** Version-aware archive source for Focus Beta. */
class FocusBetaReleaseRepository(
    private val mozillaArchiveRepository: MozillaArchiveRepository,
) : VersionAwareReleaseRepository {
    override val appName: String = FOCUS_BETA

    override suspend fun getLatestReleases(): NetworkResult<List<MozillaArchiveApk>> =
        mozillaArchiveRepository.getFocusBetaBuilds()

    override suspend fun getAvailableReleaseVersions(): NetworkResult<List<String>> =
        mozillaArchiveRepository.getFocusBetaVersions()

    override suspend fun getReleasesForVersion(version: String): NetworkResult<List<MozillaArchiveApk>> =
        mozillaArchiveRepository.getFocusBetaBuildsForVersion(version)
}
