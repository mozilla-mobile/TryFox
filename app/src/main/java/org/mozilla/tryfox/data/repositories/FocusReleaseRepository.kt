package org.mozilla.tryfox.data.repositories

import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.util.FOCUS_RELEASE

/**
 * A [ReleaseRepository] for Focus builds.
 */
class FocusReleaseRepository(
    private val mozillaArchiveRepository: MozillaArchiveRepository,
) : MajorVersionAwareReleaseRepository {
    override val appName: String = FOCUS_RELEASE

    override suspend fun getLatestReleases(): NetworkResult<List<MozillaArchiveApk>> {
        return mozillaArchiveRepository.getFocusReleaseBuilds()
    }

    override suspend fun getAvailableReleaseMajors(): NetworkResult<List<Int>> {
        return mozillaArchiveRepository.getFocusReleaseMajorVersions()
    }

    override suspend fun getReleasesForMajor(majorVersion: Int): NetworkResult<List<MozillaArchiveApk>> {
        return mozillaArchiveRepository.getFocusReleaseBuildsForMajor(majorVersion)
    }
}
