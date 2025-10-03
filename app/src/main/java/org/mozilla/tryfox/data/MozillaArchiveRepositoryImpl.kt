package org.mozilla.tryfox.data

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.format.char
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

    private suspend fun getNightlyBuilds(appName: String): NetworkResult<List<ParsedNightlyApk>> {
        val today = clock.todayIn(TimeZone.currentSystemDefault())
        val currentMonthUrl = archiveUrlForDate(appName, today)
        val result = fetchAndParseNightlyBuilds(currentMonthUrl, appName)

        if (result is NetworkResult.Error && (result.cause as? HttpException)?.code() == 404) {
            val lastMonth = today.minus(1, DateTimeUnit.MONTH)
            val lastMonthUrl = archiveUrlForDate(appName, lastMonth)
            return fetchAndParseNightlyBuilds(lastMonthUrl, appName)
        }
        return result
    }

    override suspend fun getFenixNightlyBuilds(): NetworkResult<List<ParsedNightlyApk>> = getNightlyBuilds(FENIX)

    override suspend fun getFocusNightlyBuilds(): NetworkResult<List<ParsedNightlyApk>> = getNightlyBuilds(FOCUS)

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

    private suspend fun fetchAndParseNightlyBuilds(archiveBaseUrl: String, appNameFilter: String): NetworkResult<List<ParsedNightlyApk>> {
        return try {
            val htmlResult = archiveApiService.getHtmlPage(archiveBaseUrl)
            val parsedApks = parseNightlyBuildsFromHtml(htmlResult, archiveBaseUrl, appNameFilter)
            NetworkResult.Success(parsedApks)
        } catch (e: Exception) {
            NetworkResult.Error("Failed to fetch or parse $appNameFilter builds: ${e.message}", e)
        }
    }

    private fun parseNightlyBuildsFromHtml(
        html: String,
        archiveUrl: String,
        app: String,
    ): List<ParsedNightlyApk> {
        val htmlPattern = Regex("""<td>Dir</td>\s*<td><a href="[^"]*">([^<]+/)</a></td>""")
        val rawBuildStrings = htmlPattern.findAll(html)
            .mapNotNull { it.groups[1]?.value }
            .filter { it != "../" }
            .toList()

        val buildsByDate = rawBuildStrings.groupBy { it.substringBefore("-$app") }
        if (buildsByDate.isEmpty()) return emptyList()

        val lastBuildDateStr = buildsByDate.keys.maxByOrNull(::parseDateString) ?: return emptyList()

        val lastBuildsForDate = buildsByDate[lastBuildDateStr] ?: return emptyList()

        val apkPattern =
            Pattern.compile("""^(\d{4}-\d{2}-\d{2}-\d{2}-\d{2}-\d{2})-(.*?)-([^-]+)-android-(.*?)/$""")

        return lastBuildsForDate.mapNotNull { buildString ->
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

    private fun parseDateString(dateStr: String): LocalDateTime {
        return try {
            val format = LocalDateTime.Format {
                year(); char('-'); monthNumber(); char('-'); dayOfMonth()
                char('-'); hour(); char('-'); minute(); char('-'); second()
            }
            LocalDateTime.parse(dateStr, format)
        } catch (_: Exception) {
            // Return a very old date so that it's never chosen as the max
            LocalDateTime(1, 1, 1, 0, 0)
        }
    }
}
