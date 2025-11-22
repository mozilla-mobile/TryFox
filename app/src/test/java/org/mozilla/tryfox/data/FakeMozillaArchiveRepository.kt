package org.mozilla.tryfox.data

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.data.repositories.MozillaArchiveRepository
import org.mozilla.tryfox.model.MozillaArchiveApk

/**
 * A fake implementation of [org.mozilla.tryfox.data.repositories.MozillaArchiveRepository] for use in unit tests.
 */
class FakeMozillaArchiveRepository(
    private val fenixBuilds: NetworkResult<List<MozillaArchiveApk>> = NetworkResult.Success(emptyList()),
    private val focusBuilds: NetworkResult<List<MozillaArchiveApk>> = NetworkResult.Success(emptyList()),
    private val fenixReleases: NetworkResult<List<MozillaArchiveApk>> = NetworkResult.Success(emptyList()),
) : MozillaArchiveRepository {

    override suspend fun getFenixNightlyBuilds(date: LocalDate?): NetworkResult<List<MozillaArchiveApk>> {
        return fenixBuilds
    }

    override suspend fun getFocusNightlyBuilds(date: LocalDate?): NetworkResult<List<MozillaArchiveApk>> {
        return focusBuilds
    }

    override suspend fun getFenixReleaseBuilds(releaseType: ReleaseType): NetworkResult<List<MozillaArchiveApk>> {
        return fenixReleases
    }
}
