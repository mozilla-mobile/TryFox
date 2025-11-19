package org.mozilla.tryfox.data

import kotlinx.datetime.LocalDate
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
