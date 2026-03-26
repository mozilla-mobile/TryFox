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
    private val fenixReleaseMajors: NetworkResult<List<Int>> = NetworkResult.Success(emptyList()),
    private val fenixReleasesByMajor: Map<Int, NetworkResult<List<MozillaArchiveApk>>> = emptyMap(),
    private val focusReleases: NetworkResult<List<MozillaArchiveApk>> = NetworkResult.Success(emptyList()),
    private val focusReleaseMajors: NetworkResult<List<Int>> = NetworkResult.Success(emptyList()),
    private val focusReleasesByMajor: Map<Int, NetworkResult<List<MozillaArchiveApk>>> = emptyMap(),
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

    override suspend fun getFenixReleaseMajorVersions(releaseType: ReleaseType): NetworkResult<List<Int>> {
        return fenixReleaseMajors
    }

    override suspend fun getFenixReleaseBuildsForMajor(
        majorVersion: Int,
        releaseType: ReleaseType,
    ): NetworkResult<List<MozillaArchiveApk>> {
        return fenixReleasesByMajor[majorVersion] ?: fenixReleases
    }

    override suspend fun getFocusReleaseBuilds(): NetworkResult<List<MozillaArchiveApk>> {
        return focusReleases
    }

    override suspend fun getFocusReleaseMajorVersions(): NetworkResult<List<Int>> {
        return focusReleaseMajors
    }

    override suspend fun getFocusReleaseBuildsForMajor(majorVersion: Int): NetworkResult<List<MozillaArchiveApk>> {
        return focusReleasesByMajor[majorVersion] ?: focusReleases
    }
}
