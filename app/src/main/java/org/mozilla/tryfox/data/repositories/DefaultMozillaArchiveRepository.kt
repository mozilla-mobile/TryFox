package org.mozilla.tryfox.data.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import org.mozilla.tryfox.data.MozillaArchiveHtmlParser
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.ReleaseType
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.network.MozillaArchivesApiService
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_BETA
import org.mozilla.tryfox.util.FOCUS_RELEASE
import retrofit2.HttpException
import java.net.HttpURLConnection
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

class DefaultMozillaArchiveRepository(
    private val mozillaArchivesApiService: MozillaArchivesApiService,
    private val clock: Clock = Clock.System,
    private val mozillaArchiveHtmlParser: MozillaArchiveHtmlParser = MozillaArchiveHtmlParser(),
) : MozillaArchiveRepository {

    private val listingLocks = ConcurrentHashMap<String, Mutex>()
    private val listingCache = ConcurrentHashMap<String, CachedListing>()

    companion object {
        private val LISTING_CACHE_TTL = 10.seconds
        const val ARCHIVE_MOZILLA_BASE_URL = "https://archive.mozilla.org/pub/"
        const val RELEASES_FENIX_BASE_URL = "${ARCHIVE_MOZILLA_BASE_URL}fenix/releases/"
        const val CANDIDATES_FENIX_BASE_URL = "${ARCHIVE_MOZILLA_BASE_URL}fenix/candidates/"
        const val RELEASES_FOCUS_BASE_URL = "${ARCHIVE_MOZILLA_BASE_URL}focus/releases/"

        internal fun archiveUrlForDate(appName: String, date: LocalDate): String {
            val year = date.year.toString()
            val month = date.monthNumber.toString().padStart(2, '0')

            return "${ARCHIVE_MOZILLA_BASE_URL}$appName/nightly/$year/$month/"
        }

        internal fun archiveUrlForRelease(number: String): String {
            return "${RELEASES_FENIX_BASE_URL}$number/android/"
        }

        internal fun archiveUrlForRelease(baseUrl: String, number: String): String {
            return "$baseUrl$number/android/"
        }

        internal fun archiveUrlForCandidate(version: String, buildNumber: Int): String {
            return "${CANDIDATES_FENIX_BASE_URL}$version-candidates/build$buildNumber/android/"
        }

        internal fun archiveUrlForCandidateBuilds(version: String): String {
            return "${CANDIDATES_FENIX_BASE_URL}$version-candidates/"
        }
    }

    override suspend fun getFenixNightlyBuilds(date: LocalDate?): NetworkResult<List<MozillaArchiveApk>> = getNightlyBuilds(FENIX, date)

    override suspend fun getFocusNightlyBuilds(date: LocalDate?): NetworkResult<List<MozillaArchiveApk>> = getNightlyBuilds(FOCUS, date)

    override suspend fun getFenixReleaseBuilds(releaseType: ReleaseType): NetworkResult<List<MozillaArchiveApk>> {
        return try {
            val latestReleaseVersion = mozillaArchiveHtmlParser
                .releaseVersionsFromDirectories(getDirectoryListing(RELEASES_FENIX_BASE_URL), releaseType)
                .firstOrNull()
                .orEmpty()

            if (latestReleaseVersion.isEmpty()) {
                return NetworkResult.Error("No releases found for type $releaseType", null)
            }

            fetchReleaseApksForVersion(
                version = latestReleaseVersion,
                archiveBaseUrl = RELEASES_FENIX_BASE_URL,
                archiveAppName = FENIX,
                resultAppName = if (releaseType == ReleaseType.Release) FENIX_RELEASE else FENIX_BETA,
                releaseType = releaseType,
            )
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch or parse Fenix releases: ${e.message}", e)
        }
    }

    override suspend fun getFocusReleaseBuilds(): NetworkResult<List<MozillaArchiveApk>> {
        return getFocusReleaseBuilds(ReleaseType.Release)
    }

    override suspend fun getFocusBetaBuilds(): NetworkResult<List<MozillaArchiveApk>> {
        return getFocusReleaseBuilds(ReleaseType.Beta)
    }

    private suspend fun getFocusReleaseBuilds(releaseType: ReleaseType): NetworkResult<List<MozillaArchiveApk>> {
        return try {
            val latestReleaseVersion = mozillaArchiveHtmlParser
                .releaseVersionsFromDirectories(getDirectoryListing(RELEASES_FOCUS_BASE_URL), releaseType)
                .firstOrNull()
                .orEmpty()

            if (latestReleaseVersion.isEmpty()) {
                return NetworkResult.Error("No releases found for Focus", null)
            }

            fetchReleaseApksForVersion(
                version = latestReleaseVersion,
                archiveBaseUrl = RELEASES_FOCUS_BASE_URL,
                archiveAppName = FOCUS,
                resultAppName = if (releaseType == ReleaseType.Release) FOCUS_RELEASE else FOCUS_BETA,
                releaseType = releaseType,
            )
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch or parse Focus releases: ${e.message}", e)
        }
    }

    override suspend fun getFenixReleaseVersions(releaseType: ReleaseType): NetworkResult<List<String>> {
        return try {
            val versions = mozillaArchiveHtmlParser
                .releaseVersionsFromDirectories(getDirectoryListing(RELEASES_FENIX_BASE_URL), releaseType)

            if (versions.isEmpty()) {
                return NetworkResult.Error("No release versions found for type $releaseType", null)
            }

            NetworkResult.Success(versions)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch Fenix release versions: ${e.message}", e)
        }
    }

    override suspend fun getFenixCandidateBaseVersions(releaseType: ReleaseType): NetworkResult<List<String>> {
        return try {
            NetworkResult.Success(
                mozillaArchiveHtmlParser
                    .candidateBaseVersionsFromDirectories(getDirectoryListing(CANDIDATES_FENIX_BASE_URL), releaseType),
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch Fenix candidates: ${e.message}", e)
        }
    }

    override suspend fun getFenixCandidateVersions(baseVersion: String): NetworkResult<List<String>> {
        return try {
            val buildsHtml = mozillaArchivesApiService.getHtmlPage(archiveUrlForCandidateBuilds(baseVersion))
            NetworkResult.Success(
                mozillaArchiveHtmlParser.parseCandidateBuildNumbersFromHtml(buildsHtml)
                    .map { buildNumber -> "$baseVersion-RC$buildNumber" },
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch Fenix $baseVersion candidates: ${e.message}", e)
        }
    }

    override suspend fun getFocusReleaseVersions(): NetworkResult<List<String>> {
        return getFocusReleaseVersions(ReleaseType.Release)
    }

    override suspend fun getFocusBetaVersions(): NetworkResult<List<String>> {
        return getFocusReleaseVersions(ReleaseType.Beta)
    }

    private suspend fun getFocusReleaseVersions(releaseType: ReleaseType): NetworkResult<List<String>> {
        return try {
            val releaseVersions = mozillaArchiveHtmlParser
                .releaseVersionsFromDirectories(getDirectoryListing(RELEASES_FOCUS_BASE_URL), releaseType)

            if (releaseVersions.isEmpty()) {
                return NetworkResult.Error("No Focus release versions found", null)
            }

            NetworkResult.Success(releaseVersions)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch Focus release versions: ${e.message}", e)
        }
    }

    override suspend fun getFenixReleaseBuildsForVersion(
        version: String,
        releaseType: ReleaseType,
    ): NetworkResult<List<MozillaArchiveApk>> {
        return try {
            if (version.isEmpty()) {
                return NetworkResult.Error("No version provided", null)
            }

            val candidate = parseCandidateVersion(version)
            if (candidate == null) {
                fetchReleaseApksForVersion(
                    version = version,
                    archiveBaseUrl = RELEASES_FENIX_BASE_URL,
                    archiveAppName = FENIX,
                    resultAppName = if (releaseType == ReleaseType.Release) FENIX_RELEASE else FENIX_BETA,
                    releaseType = releaseType,
                )
            } else {
                fetchReleaseApksForVersion(
                    version = candidate.baseVersion,
                    displayVersion = version,
                    archiveUrl = archiveUrlForCandidate(candidate.baseVersion, candidate.buildNumber),
                    archiveAppName = FENIX,
                    resultAppName = if (releaseType == ReleaseType.Release) FENIX_RELEASE else FENIX_BETA,
                    cacheBuildKey = "candidate-${candidate.baseVersion}-build${candidate.buildNumber}",
                )
            }
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch Fenix release $version: ${e.message}", e)
        }
    }

    override suspend fun getFocusReleaseBuildsForVersion(version: String): NetworkResult<List<MozillaArchiveApk>> {
        return getFocusReleaseBuildsForVersion(version, ReleaseType.Release)
    }

    override suspend fun getFocusBetaBuildsForVersion(version: String): NetworkResult<List<MozillaArchiveApk>> {
        return getFocusReleaseBuildsForVersion(version, ReleaseType.Beta)
    }

    private suspend fun getFocusReleaseBuildsForVersion(version: String, releaseType: ReleaseType): NetworkResult<List<MozillaArchiveApk>> {
        return try {
            if (version.isEmpty()) {
                return NetworkResult.Error("No version provided", null)
            }

            fetchReleaseApksForVersion(
                version = version,
                archiveBaseUrl = RELEASES_FOCUS_BASE_URL,
                archiveAppName = FOCUS,
                resultAppName = if (releaseType == ReleaseType.Release) FOCUS_RELEASE else FOCUS_BETA,
                releaseType = releaseType,
            )
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch Focus release $version: ${e.message}", e)
        }
    }

    private suspend fun fetchReleaseApksForVersion(
        version: String,
        archiveBaseUrl: String,
        archiveAppName: String,
        resultAppName: String,
        releaseType: ReleaseType,
    ): NetworkResult<List<MozillaArchiveApk>> {
        val releaseUrl = archiveUrlForRelease(archiveBaseUrl, version)
        return fetchReleaseApksForVersion(
            version = version,
            displayVersion = version,
            archiveUrl = releaseUrl,
            archiveAppName = archiveAppName,
            resultAppName = resultAppName,
            cacheBuildKey = "",
        )
    }

    private suspend fun fetchReleaseApksForVersion(
        version: String,
        displayVersion: String,
        archiveUrl: String,
        archiveAppName: String,
        resultAppName: String,
        // Empty for releases; candidates need an isolated cache key.
        cacheBuildKey: String,
    ): NetworkResult<List<MozillaArchiveApk>> {
        val releaseHtml = mozillaArchivesApiService.getHtmlPage(archiveUrl)
        val apks = mozillaArchiveHtmlParser.parseApkDirectoriesFromHtml(releaseHtml)
            .filter { it.appName == archiveAppName }
            .map { it.toApk(archiveUrl, appName = resultAppName, displayVersion = displayVersion, buildKey = cacheBuildKey) }

        if (apks.isEmpty()) {
            return NetworkResult.Error("No ABIs found for release $version", null)
        }

        return NetworkResult.Success(apks)
    }

    override suspend fun isPublished(url: String): Boolean = try {
        // Only archive URLs are built from directory listings; others come from real asset links.
        !url.startsWith(ARCHIVE_MOZILLA_BASE_URL) ||
            mozillaArchivesApiService.head(url).code() != HttpURLConnection.HTTP_NOT_FOUND
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        true
    }

    private data class CandidateVersion(val baseVersion: String, val buildNumber: Int)

    private class CachedListing(val fetchedAt: Instant, val directories: List<String>)

    /**
     * Fetches an index page such as `releases/` as its directory names. Release and beta cards load
     * concurrently and read the same listings, so a per-URL lock makes the second caller wait for
     * the first's request, and successful results are reused for [LISTING_CACHE_TTL]; the short TTL
     * keeps a pull-to-refresh fetching fresh data. Different URLs are fetched in parallel.
     */
    private suspend fun getDirectoryListing(url: String): List<String> = listingLocks.getOrPut(url) { Mutex() }.withLock {
        val now = clock.now()
        listingCache[url]?.takeIf { now - it.fetchedAt < LISTING_CACHE_TTL }?.let { return@withLock it.directories }
        mozillaArchiveHtmlParser.parseDirectoryNamesFromHtml(mozillaArchivesApiService.getHtmlPage(url))
            .also { listingCache[url] = CachedListing(now, it) }
    }

    private fun parseCandidateVersion(version: String): CandidateVersion? {
        val match = Regex("^(\\d+\\.\\d+(?:\\.\\d+)?|\\d+\\.\\d+b\\d+)-RC(\\d+)$").matchEntire(version) ?: return null
        return CandidateVersion(match.groupValues[1], match.groupValues[2].toInt())
    }

    private suspend fun getNightlyBuilds(appName: String, date: LocalDate? = null): NetworkResult<List<MozillaArchiveApk>> {
        if (date != null) {
            val url = archiveUrlForDate(appName, date)
            return fetchAndParseNightlyBuilds(url, appName, date)
        }

        val today = clock.todayIn(TimeZone.currentSystemDefault())
        val currentMonthUrl = archiveUrlForDate(appName, today)
        val result = fetchAndParseNightlyBuilds(currentMonthUrl, appName, null)

        if (result is NetworkResult.Error && (result.cause as? HttpException)?.code() == 404) {
            val lastMonth = today.minus(1, DateTimeUnit.MONTH)
            val lastMonthUrl = archiveUrlForDate(appName, lastMonth)
            return fetchAndParseNightlyBuilds(lastMonthUrl, appName, null)
        }
        return result
    }

    private suspend fun fetchAndParseNightlyBuilds(archiveBaseUrl: String, appNameFilter: String, date: LocalDate?): NetworkResult<List<MozillaArchiveApk>> {
        return try {
            val htmlResult = mozillaArchivesApiService.getHtmlPage(archiveBaseUrl)
            val parsedApks = mozillaArchiveHtmlParser.parseNightlyBuildsFromHtml(htmlResult, archiveBaseUrl, date)
            NetworkResult.Success(parsedApks)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch or parse $appNameFilter builds: ${e.message}", e)
        }
    }
}
