package org.mozilla.tryfox.data

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.model.MozillaArchiveApk

interface MozillaArchiveRepository {
    /**
     * Fetches and parses the list of Fenix nightly builds for the current month from the archive.
     */
    suspend fun getFenixNightlyBuilds(date: LocalDate? = null): NetworkResult<List<MozillaArchiveApk>>

    /**
     * Fetches and parses the list of Focus nightly builds for the current month from the archive.
     */
    suspend fun getFocusNightlyBuilds(date: LocalDate? = null): NetworkResult<List<MozillaArchiveApk>>

    /**
     * Fetches and parses the latest Fenix release APKs from the archive.
     * @param releaseType The type of release to fetch (Beta or Release)
     * @return List of available MozillaArchiveApk for the release
     */
    suspend fun getFenixReleaseBuilds(releaseType: ReleaseType = ReleaseType.Beta): NetworkResult<List<MozillaArchiveApk>>
}
