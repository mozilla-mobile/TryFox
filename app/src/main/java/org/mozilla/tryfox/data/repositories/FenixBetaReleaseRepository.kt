package org.mozilla.tryfox.data.repositories

import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.ReleaseType
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.util.FENIX_BETA

/**
 * A [ReleaseRepository] for Fenix builds.
 */
class FenixBetaReleaseRepository(
    private val mozillaArchiveRepository: MozillaArchiveRepository,
) : VersionAwareReleaseRepository {
    override val appName: String = FENIX_BETA

    override suspend fun getLatestReleases(): NetworkResult<List<MozillaArchiveApk>> {
        return mozillaArchiveRepository.getFenixReleaseBuilds(ReleaseType.Beta)
    }

    override suspend fun getAvailableReleaseVersions(): NetworkResult<List<String>> {
        return mozillaArchiveRepository.getFenixReleaseVersions(ReleaseType.Beta)
    }

    override suspend fun getReleasesForVersion(version: String): NetworkResult<List<MozillaArchiveApk>> {
        return mozillaArchiveRepository.getFenixReleaseBuildsForVersion(version, ReleaseType.Beta)
    }
}
