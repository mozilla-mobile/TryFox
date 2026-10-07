package org.mozilla.tryfox.data.repositories

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.model.MozillaArchiveApk

/**
 * Interface for a repository that provides release information for a specific application.
 */
interface ReleaseRepository {
    /**
     * The name of the application this repository is for.
     */
    val appName: String

    /**
     * Fetches the latest releases for the application.
     */
    suspend fun getLatestReleases(): NetworkResult<List<MozillaArchiveApk>>
}

interface DateAwareReleaseRepository : ReleaseRepository {
    suspend fun getReleases(date: LocalDate? = null): NetworkResult<List<MozillaArchiveApk>>
}

interface VersionAwareReleaseRepository : ReleaseRepository {
    suspend fun getAvailableReleaseVersions(): NetworkResult<List<String>>

    /** Base versions that have release candidates, from a single listing request. */
    suspend fun getCandidateBaseVersions(): NetworkResult<List<String>> = NetworkResult.Success(emptyList())

    /** RC versions for one candidate base version, selectable via [getReleasesForVersion]. */
    suspend fun getCandidateVersions(baseVersion: String): NetworkResult<List<String>> = NetworkResult.Success(emptyList())

    suspend fun getReleasesForVersion(version: String): NetworkResult<List<MozillaArchiveApk>>
}
