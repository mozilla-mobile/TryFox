package org.mozilla.tryfox.data.repositories

import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.ReleaseType
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.util.FENIX_RELEASE

/**
 * A [ReleaseRepository] for Fenix builds.
 */
class FenixReleaseReleaseRepository(
    private val mozillaArchiveRepository: MozillaArchiveRepository,
) : ReleaseRepository {
    override val appName: String = FENIX_RELEASE

    override suspend fun getLatestReleases(): NetworkResult<List<MozillaArchiveApk>> {
        return mozillaArchiveRepository.getFenixReleaseBuilds(ReleaseType.Release)
    }
}
