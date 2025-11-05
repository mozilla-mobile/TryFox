package org.mozilla.tryfox.data

import org.mozilla.tryfox.model.ParsedNightlyApk
import org.mozilla.tryfox.network.GithubApiService
import org.mozilla.tryfox.util.TRYFOX

/**
 * A [ReleaseRepository] for TryFox builds.
 */
class TryFoxReleaseRepository(
    private val githubApiService: GithubApiService,
) : ReleaseRepository {
    override val appName: String = TRYFOX

    override suspend fun getLatestReleases(): NetworkResult<List<ParsedNightlyApk>> {
        return try {
            val release = githubApiService.getLatestGitHubRelease("mozilla-mobile", "TryFox")
            val parsedApks = release.assets.map { asset ->
                ParsedNightlyApk(
                    originalString = asset.name,
                    rawDateString = release.updatedAt,
                    appName = TRYFOX,
                    version = release.tagName,
                    abiName = "universal",
                    fullUrl = asset.browserDownloadUrl,
                    fileName = asset.name,
                )
            }
            NetworkResult.Success(parsedApks)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch TryFox releases: ${e.message}", e)
        }
    }
}
