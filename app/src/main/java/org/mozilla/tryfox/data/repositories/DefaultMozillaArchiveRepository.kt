package org.mozilla.tryfox.data.repositories

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
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

class DefaultMozillaArchiveRepository(
    private val mozillaArchivesApiService: MozillaArchivesApiService,
    private val clock: Clock = Clock.System,
    private val mozillaArchiveHtmlParser: MozillaArchiveHtmlParser = MozillaArchiveHtmlParser(),
) : MozillaArchiveRepository {

    companion object {
        private const val CANDIDATE_BUILD_INDEX_CONCURRENCY = 4
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
            val releasesHtml = mozillaArchivesApiService.getHtmlPage(RELEASES_FENIX_BASE_URL)
            val latestReleaseVersion = mozillaArchiveHtmlParser.parseFenixReleasesFromHtml(releasesHtml, releaseType)

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
            val releasesHtml = mozillaArchivesApiService.getHtmlPage(RELEASES_FOCUS_BASE_URL)
            val latestReleaseVersion = mozillaArchiveHtmlParser.parseFenixReleasesFromHtml(
                releasesHtml,
                releaseType,
            )

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
            val releasesHtml = mozillaArchivesApiService.getHtmlPage(RELEASES_FENIX_BASE_URL)
            val releaseVersions = mozillaArchiveHtmlParser.parseFenixReleaseVersionsFromHtml(releasesHtml, releaseType)
            val candidateVersions = fetchFenixCandidateVersions(releaseType, releaseVersions.toSet())
            val versions = (releaseVersions + candidateVersions)
                .distinct()
                .sortedWith(mozillaArchiveHtmlParser::compareReleaseVersions)
                .reversed()

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

    override suspend fun getFocusReleaseVersions(): NetworkResult<List<String>> {
        return getFocusReleaseVersions(ReleaseType.Release)
    }

    override suspend fun getFocusBetaVersions(): NetworkResult<List<String>> {
        return getFocusReleaseVersions(ReleaseType.Beta)
    }

    private suspend fun getFocusReleaseVersions(releaseType: ReleaseType): NetworkResult<List<String>> {
        return try {
            val releasesHtml = mozillaArchivesApiService.getHtmlPage(RELEASES_FOCUS_BASE_URL)
            val releaseVersions = mozillaArchiveHtmlParser.parseFenixReleaseVersionsFromHtml(releasesHtml, releaseType)

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
        cacheBuildKey: String,
    ): NetworkResult<List<MozillaArchiveApk>> {
        val releaseHtml = mozillaArchivesApiService.getHtmlPage(archiveUrl)
        val abis = mozillaArchiveHtmlParser.parseFenixReleaseAbisFromHtml(releaseHtml, archiveAppName)

        if (abis.isEmpty()) {
            return NetworkResult.Error("No ABIs found for release $version", null)
        }

        val apks = abis.map { abi ->
            constructReleaseApk(version, displayVersion, abi, archiveUrl, archiveAppName, resultAppName, cacheBuildKey)
        }

        if (apks.isEmpty()) {
            return NetworkResult.Error("Failed to construct APKs for release $version", null)
        }

        return NetworkResult.Success(apks)
    }

    private fun constructReleaseApk(
        version: String,
        displayVersion: String,
        abi: String,
        releaseBaseUrl: String,
        archiveAppName: String,
        resultAppName: String,
        cacheBuildKey: String,
    ): MozillaArchiveApk {
        val buildString = "$archiveAppName-$version-android${if (abi == "universal") "" else "-$abi"}/"
        val fileName = "$archiveAppName-$version.multi.android-$abi.apk"
        val fullUrl = "${releaseBaseUrl}${buildString}$fileName"

        return MozillaArchiveApk(
            originalString = buildString,
            rawDateString = cacheBuildKey, // Empty for releases; candidates need an isolated cache key.
            appName = resultAppName,
            version = displayVersion,
            abiName = abi,
            fullUrl = fullUrl,
            fileName = fileName,
        )
    }

    private suspend fun fetchFenixCandidateVersions(
        releaseType: ReleaseType,
        publishedVersions: Set<String>,
    ): List<String> {
        val candidatesHtml = getHtmlPageOrNull(CANDIDATES_FENIX_BASE_URL) ?: return emptyList()
        val candidateBases = mozillaArchiveHtmlParser
            .parseFenixCandidateVersionsFromHtml(candidatesHtml, releaseType)
            .filterNot(publishedVersions::contains)

        return coroutineScope {
            candidateBases
                .chunked(CANDIDATE_BUILD_INDEX_CONCURRENCY)
                .flatMap { candidates ->
                    candidates.map { baseVersion ->
                        async {
                            val buildsHtml = getHtmlPageOrNull(archiveUrlForCandidateBuilds(baseVersion))
                                ?: return@async emptyList()
                            mozillaArchiveHtmlParser.parseCandidateBuildNumbersFromHtml(buildsHtml)
                                .map { buildNumber -> "$baseVersion-RC$buildNumber" }
                        }
                    }.awaitAll().flatten()
                }
        }
    }

    private data class CandidateVersion(val baseVersion: String, val buildNumber: Int)

    private suspend fun getHtmlPageOrNull(url: String): String? = try {
        mozillaArchivesApiService.getHtmlPage(url)
    } catch (e: CancellationException) {
        throw e
    } catch (_: Exception) {
        null
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
