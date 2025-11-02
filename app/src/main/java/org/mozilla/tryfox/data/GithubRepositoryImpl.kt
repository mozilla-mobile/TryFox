package org.mozilla.tryfox.data

import org.mozilla.tryfox.model.ParsedNightlyApk
import org.mozilla.tryfox.network.GithubApiService

class GithubRepositoryImpl(
    private val githubApiService: GithubApiService,
) : GithubRepository {

    override suspend fun getTryFoxReleases(): NetworkResult<List<ParsedNightlyApk>> {
        return try {
            val release = githubApiService.getLatestGitHubRelease("mozilla-mobile", "TryFox")
            val parsedApks = release.assets.map { asset ->
                ParsedNightlyApk(
                    originalString = asset.name,
                    rawDateString = release.updatedAt,
                    appName = "TryFox",
                    version = release.tagName,
                    abiName = "universal",
                    fullUrl = asset.browserDownloadUrl,
                    fileName = asset.name
                )
            }
            NetworkResult.Success(parsedApks)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch TryFox releases: ${e.message}", e)
        }
    }
}
