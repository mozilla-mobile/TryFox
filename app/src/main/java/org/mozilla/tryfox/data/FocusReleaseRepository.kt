package org.mozilla.tryfox.data

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.util.FOCUS

/**
 * A [ReleaseRepository] for Focus builds.
 */
class FocusReleaseRepository(
    private val mozillaArchiveRepository: MozillaArchiveRepository,
) : DateAwareReleaseRepository {
    override val appName: String = FOCUS

    override suspend fun getLatestReleases(): NetworkResult<List<MozillaArchiveApk>> {
        return mozillaArchiveRepository.getFocusNightlyBuilds()
    }

    override suspend fun getReleases(date: LocalDate?): NetworkResult<List<MozillaArchiveApk>> {
        return mozillaArchiveRepository.getFocusNightlyBuilds(date)
    }
}
