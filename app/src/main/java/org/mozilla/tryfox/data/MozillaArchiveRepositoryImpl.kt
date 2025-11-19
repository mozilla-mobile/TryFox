package org.mozilla.tryfox.data

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.network.MozillaArchivesApiService
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FENIX_RELEASE
import org.mozilla.tryfox.util.FENIX_BETA
import org.mozilla.tryfox.util.FOCUS
import retrofit2.HttpException

class MozillaArchiveRepositoryImpl(
    private val mozillaArchivesApiService: MozillaArchivesApiService,
    private val clock: Clock = Clock.System,
    private val mozillaArchiveHtmlParser: MozillaArchiveHtmlParser = MozillaArchiveHtmlParser(),
) : MozillaArchiveRepository {

    companion object {
        const val ARCHIVE_MOZILLA_BASE_URL = "https://archive.mozilla.org/pub/"
        const val RELEASES_FENIX_BASE_URL = "${ARCHIVE_MOZILLA_BASE_URL}fenix/releases/"

        internal fun archiveUrlForDate(appName: String, date: LocalDate): String {
            val year = date.year.toString()
            val month = date.monthNumber.toString().padStart(2, '0')

            return "${ARCHIVE_MOZILLA_BASE_URL}$appName/nightly/$year/$month/"
        }

        internal fun archiveUrlForRelease(number: String): String {
            return "${RELEASES_FENIX_BASE_URL}${number}/android/"
        }
    }

    override suspend fun getFenixNightlyBuilds(date: LocalDate?): NetworkResult<List<MozillaArchiveApk>> = getNightlyBuilds(FENIX, date)

    override suspend fun getFocusNightlyBuilds(date: LocalDate?): NetworkResult<List<MozillaArchiveApk>> = getNightlyBuilds(FOCUS, date)

    override suspend fun getFenixReleaseBuilds(releaseType: ReleaseType): NetworkResult<List<MozillaArchiveApk>> {
        return try {
            // Get the latest release version
            val releasesPageUrl = RELEASES_FENIX_BASE_URL
            val releasesHtml = mozillaArchivesApiService.getHtmlPage(releasesPageUrl)
            val latestReleaseVersion = mozillaArchiveHtmlParser.parseFenixReleasesFromHtml(releasesHtml, releaseType)
            
            if (latestReleaseVersion.isEmpty()) {
                return NetworkResult.Error("No releases found for type $releaseType", null)
            }
            
            // Get the release directory listing to find available ABIs
            val releaseUrl = archiveUrlForRelease(latestReleaseVersion)
            val releaseHtml = mozillaArchivesApiService.getHtmlPage(releaseUrl)
            val abis = mozillaArchiveHtmlParser.parseFenixReleaseAbisFromHtml(releaseHtml, FENIX)
            
            if (abis.isEmpty()) {
                return NetworkResult.Error("No ABIs found for release $latestReleaseVersion", null)
            }
            
            val apks = abis.map { abi ->
                constructReleaseApk(latestReleaseVersion, abi, releaseUrl, releaseType)
            }
            
            if (apks.isEmpty()) {
                return NetworkResult.Error("Failed to construct APKs for release $latestReleaseVersion", null)
            }
            
            NetworkResult.Success(apks)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch or parse Fenix releases: ${e.message}", e)
        }
    }

    private fun constructReleaseApk(version: String, abi: String, releaseBaseUrl: String, releaseType: ReleaseType): MozillaArchiveApk {
        val buildString = "fenix-$version-android${if (abi == "universal") "" else "-$abi"}/"
        val fileName = "fenix-$version.multi.android-$abi.apk"
        val fullUrl = "${releaseBaseUrl}${buildString}${fileName}"
        
        val appName = when (releaseType) {
            ReleaseType.Release -> FENIX_RELEASE
            ReleaseType.Beta -> FENIX_BETA
        }

        return MozillaArchiveApk(
            originalString = buildString,
            rawDateString = "", // Release builds don't have date strings
            appName = appName,
            version = version,
            abiName = abi,
            fullUrl = fullUrl,
            fileName = fileName,
        )
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
