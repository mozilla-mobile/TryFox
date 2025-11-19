package org.mozilla.tryfox.data

import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.util.FENIX_BETA

/**
 * A [ReleaseRepository] for Fenix builds.
 */
class FenixBetaReleaseRepository(
    private val mozillaArchiveRepository: MozillaArchiveRepository,
) : ReleaseRepository {
    override val appName: String = FENIX_BETA

    override suspend fun getLatestReleases(): NetworkResult<List<MozillaArchiveApk>> {
        return mozillaArchiveRepository.getFenixReleaseBuilds(ReleaseType.Beta)
    }
}
