package org.mozilla.tryfox.data

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.model.ParsedNightlyApk
import org.mozilla.tryfox.util.FENIX

/**
 * A [ReleaseRepository] for Fenix builds.
 */
class FenixReleaseRepository(
    private val mozillaArchiveRepository: MozillaArchiveRepository,
) : DateAwareReleaseRepository {
    override val appName: String = FENIX

    override suspend fun getLatestReleases(): NetworkResult<List<ParsedNightlyApk>> {
        return mozillaArchiveRepository.getFenixNightlyBuilds()
    }

    override suspend fun getReleases(date: LocalDate?): NetworkResult<List<ParsedNightlyApk>> {
        return mozillaArchiveRepository.getFenixNightlyBuilds(date)
    }
}
