package org.mozilla.tryfox.data

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn
import org.mozilla.tryfox.model.ParsedNightlyApk
import org.mozilla.tryfox.network.ApiService
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FOCUS
import retrofit2.HttpException
import java.util.regex.Pattern

class MozillaArchiveRepositoryImpl(
    private val archiveApiService: ApiService,
    private val clock: Clock = Clock.System,
) : MozillaArchiveRepository {

    companion object {
        const val ARCHIVE_MOZILLA_BASE_URL = "https://archive.mozilla.org/"
        private const val REFERENCE_BROWSER_TASK_BASE_URL = "https://firefox-ci-tc.services.mozilla.com/api/index/v1/task/mobile.v2.reference-browser.nightly.latest."
        private val REFERENCE_BROWSER_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

        internal fun archiveUrlForDate(appName: String, date: LocalDate): String {
            val year = date.year.toString()
            val month = date.monthNumber.toString().padStart(2, '0')

            return "${ARCHIVE_MOZILLA_BASE_URL}pub/$appName/nightly/$year/$month/"
        }
    }

    private suspend fun getNightlyBuilds(appName: String, date: LocalDate? = null): NetworkResult<List<ParsedNightlyApk>> {
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

    override suspend fun getFenixNightlyBuilds(date: LocalDate?): NetworkResult<List<ParsedNightlyApk>> = getNightlyBuilds(FENIX, date)

    override suspend fun getFocusNightlyBuilds(date: LocalDate?): NetworkResult<List<ParsedNightlyApk>> = getNightlyBuilds(FOCUS, date)

    override suspend fun getReferenceBrowserNightlyBuilds(): NetworkResult<List<ParsedNightlyApk>> {
        return try {
            val parsedApks = REFERENCE_BROWSER_ABIS.map { abi ->
                val fullUrl = "${REFERENCE_BROWSER_TASK_BASE_URL}$abi/artifacts/public/target.$abi.apk"
                val fileName = "target.$abi.apk"
                ParsedNightlyApk(
                    originalString = "reference-browser-latest-android-$abi/",
                    rawDateString = null,
                    appName = "reference-browser",
                    version = "",
                    abiName = abi,
                    fullUrl = fullUrl,
                    fileName = fileName,
                )
            }
            NetworkResult.Success(parsedApks)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to construct Reference Browser builds: ${e.message}", e)
        }
    }

    private suspend fun fetchAndParseNightlyBuilds(archiveBaseUrl: String, appNameFilter: String, date: LocalDate?): NetworkResult<List<ParsedNightlyApk>> {
        return try {
            val htmlResult = archiveApiService.getHtmlPage(archiveBaseUrl)
            val parsedApks = parseNightlyBuildsFromHtml(htmlResult, archiveBaseUrl, appNameFilter, date)
            NetworkResult.Success(parsedApks)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch or parse $appNameFilter builds: ${e.message}", e)
        }
    }

    private fun parseNightlyBuildsFromHtml(
        html: String,
        archiveUrl: String,
        app: String,
        date: LocalDate?,
    ): List<ParsedNightlyApk> {
        val htmlPattern = Regex("<td>Dir</td>\\s*<td><a href=\"[^\"]*\">([^<]+/)</a></td>")
        val rawBuildStrings = htmlPattern.findAll(html)
            .mapNotNull { it.groups[1]?.value }
            .filter { it != "../" }
            .toList()

        val buildsForDate = if (date != null) {
            val dateString = date.toString()
            rawBuildStrings.filter { it.startsWith(dateString) }
        } else {
            val buildsByDay = rawBuildStrings.groupBy { it.substring(0, 10) }
            if (buildsByDay.isEmpty()) return emptyList()
            val latestDay = buildsByDay.keys.maxOrNull() ?: return emptyList()
            buildsByDay[latestDay] ?: emptyList()
        }

        val apkPattern =
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})-(.*?)-([^-]+)-android-(.*?)/$")

        return buildsForDate.mapNotNull { buildString ->
            val matcher = apkPattern.matcher(buildString)
            if (matcher.matches()) {
                val rawDate = matcher.group(1) ?: ""
                val appNameResult = matcher.group(2) ?: ""
                val version = matcher.group(3) ?: ""
                val abi = matcher.group(4) ?: ""

                val fileName = "$appNameResult-$version.multi.android-$abi.apk"
                val fullUrl = "${archiveUrl}${buildString}$fileName"

                ParsedNightlyApk(
                    originalString = buildString,
                    rawDateString = rawDate,
                    appName = appNameResult,
                    version = version,
                    abiName = abi,
                    fullUrl = fullUrl,
                    fileName = fileName,
                )
            } else {
                null
            }
        }
    }
}
