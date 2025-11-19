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
import org.mozilla.tryfox.util.FOCUS
import retrofit2.HttpException

class MozillaArchiveRepositoryImpl(
    private val mozillaArchivesApiService: MozillaArchivesApiService,
    private val clock: Clock = Clock.System,
    private val mozillaArchiveHtmlParser: MozillaArchiveHtmlParser = MozillaArchiveHtmlParser(),
) : MozillaArchiveRepository {

    companion object {
        const val ARCHIVE_MOZILLA_BASE_URL = "https://archive.mozilla.org/"

        internal fun archiveUrlForDate(appName: String, date: LocalDate): String {
            val year = date.year.toString()
            val month = date.monthNumber.toString().padStart(2, '0')

            return "${ARCHIVE_MOZILLA_BASE_URL}pub/$appName/nightly/$year/$month/"
        }
    }

    override suspend fun getFenixNightlyBuilds(date: LocalDate?): NetworkResult<List<MozillaArchiveApk>> = getNightlyBuilds(FENIX, date)

    override suspend fun getFocusNightlyBuilds(date: LocalDate?): NetworkResult<List<MozillaArchiveApk>> = getNightlyBuilds(FOCUS, date)

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
