package org.mozilla.tryfox.data

import org.mozilla.tryfox.model.ParsedNightlyApk

/**
 * Repository for fetching release data from GitHub.
 */
interface GithubRepository {
    /**
     * Fetches and parses the list of TryFox releases from GitHub.
     */
    suspend fun getTryFoxReleases(): NetworkResult<List<ParsedNightlyApk>>
}
