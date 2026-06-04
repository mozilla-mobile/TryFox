package org.mozilla.tryfox.data.repositories

import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.util.FOCUS_RELEASE

/**
 * A [ReleaseRepository] for Focus builds.
 */
class FocusReleaseRepository(
    private val mozillaArchiveRepository: MozillaArchiveRepository,
) : VersionAwareReleaseRepository {
    override val appName: String = FOCUS_RELEASE

    override suspend fun getLatestReleases(): NetworkResult<List<MozillaArchiveApk>> {
        return mozillaArchiveRepository.getFocusReleaseBuilds()
    }

    override suspend fun getAvailableReleaseVersions(): NetworkResult<List<String>> {
        return mozillaArchiveRepository.getFocusReleaseVersions()
    }

    override suspend fun getReleasesForVersion(version: String): NetworkResult<List<MozillaArchiveApk>> {
        return mozillaArchiveRepository.getFocusReleaseBuildsForVersion(version)
    }
}
