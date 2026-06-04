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
    private val fenixReleaseVersions: NetworkResult<List<String>> = NetworkResult.Success(emptyList()),
    private val fenixReleasesByVersion: Map<String, NetworkResult<List<MozillaArchiveApk>>> = emptyMap(),
    private val focusReleases: NetworkResult<List<MozillaArchiveApk>> = NetworkResult.Success(emptyList()),
    private val focusReleaseVersions: NetworkResult<List<String>> = NetworkResult.Success(emptyList()),
    private val focusReleasesByVersion: Map<String, NetworkResult<List<MozillaArchiveApk>>> = emptyMap(),
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

    override suspend fun getFenixReleaseVersions(releaseType: ReleaseType): NetworkResult<List<String>> {
        return fenixReleaseVersions
    }

    override suspend fun getFenixReleaseBuildsForVersion(
        version: String,
        releaseType: ReleaseType,
    ): NetworkResult<List<MozillaArchiveApk>> {
        return fenixReleasesByVersion[version] ?: fenixReleases
    }

    override suspend fun getFocusReleaseBuilds(): NetworkResult<List<MozillaArchiveApk>> {
        return focusReleases
    }

    override suspend fun getFocusReleaseVersions(): NetworkResult<List<String>> {
        return focusReleaseVersions
    }

    override suspend fun getFocusReleaseBuildsForVersion(version: String): NetworkResult<List<MozillaArchiveApk>> {
        return focusReleasesByVersion[version] ?: focusReleases
    }
}
