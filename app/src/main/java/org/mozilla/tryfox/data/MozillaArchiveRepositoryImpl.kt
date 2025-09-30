package org.mozilla.tryfox.data

import org.mozilla.tryfox.model.ParsedNightlyApk
import org.mozilla.tryfox.network.ApiService
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FOCUS
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import java.util.Calendar
import java.util.Locale

class MozillaArchiveRepositoryImpl(
    private val archiveApiService: ApiService
) : MozillaArchiveRepository {

    companion object {
        const val ARCHIVE_MOZILLA_BASE_URL = "https://archive.mozilla.org/"
        private const val REFERENCE_BROWSER_TASK_BASE_URL = "https://firefox-ci-tc.services.mozilla.com/api/index/v1/task/mobile.v2.reference-browser.nightly.latest."
        private val REFERENCE_BROWSER_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64")

        internal val fenixArchiveUrl: String get() = archiveUrl(FENIX)
        internal val focusArchiveUrl: String get() = archiveUrl(FOCUS)

        private fun archiveUrl(appName: String): String {
            val calendar = Calendar.getInstance()
            val year = calendar.get(Calendar.YEAR).toString()
            val month = String.format(
                Locale.US,
                "%02d",
                calendar.get(Calendar.MONTH) + 1
            ) // Month is 0-indexed

            return "${ARCHIVE_MOZILLA_BASE_URL}pub/$appName/nightly/$year/$month/"
        }
    }

    override suspend fun getFenixNightlyBuilds(): NetworkResult<List<ParsedNightlyApk>> {
        return fetchAndParseNightlyBuilds(fenixArchiveUrl, "fenix")
    }

    override suspend fun getFocusNightlyBuilds(): NetworkResult<List<ParsedNightlyApk>> {
        return fetchAndParseNightlyBuilds(focusArchiveUrl, "focus")
    }

    override suspend fun getReferenceBrowserNightlyBuilds(): NetworkResult<List<ParsedNightlyApk>> {
        return try {
            val parsedApks = REFERENCE_BROWSER_ABIS.map { abi ->
                val fullUrl = "${REFERENCE_BROWSER_TASK_BASE_URL}${abi}/artifacts/public/target.${abi}.apk"
                val fileName = "target.${abi}.apk"
                ParsedNightlyApk(
                    originalString = "reference-browser-latest-android-${abi}/",
                    rawDateString = null,
                    appName = "reference-browser",
                    version = "",
                    abiName = abi,
                    fullUrl = fullUrl,
                    fileName = fileName
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
        archiveUrl: String, // This parameter is the fully constructed URL for the month
        app: String // "fenix" or "focus"
    ): List<ParsedNightlyApk> {
        val rawBuildStrings = mutableListOf<String>()
        val htmlPattern =
            Pattern.compile("""<td>Dir</td>\s*<td><a href="[^"]*">([^<]+/)</a></td>""")
        val htmlMatcher = htmlPattern.matcher(html)
        while (htmlMatcher.find()) {
            htmlMatcher.group(1)?.let {
                if (it != "../") {
                    rawBuildStrings.add(it)
                }
            }
        }

        val buildsByDate = rawBuildStrings.groupBy { it.substringBefore("-$app") }
        if (buildsByDate.isEmpty()) return emptyList()

        val lastBuildDateStr = buildsByDate.keys.maxByOrNull { dateStr ->
            try {
                LocalDateTime.parse(dateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss"))
            } catch (e: Exception) {
                LocalDateTime.MIN
            }
        } ?: return emptyList()

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

                val fileName = "${appNameResult}-${version}.multi.android-${abi}.apk"
                val fullUrl = "${archiveUrl}${buildString}${fileName}"

                ParsedNightlyApk(
                    originalString = buildString,
                    rawDateString = rawDate,
                    appName = appNameResult,
                    version = version,
                    abiName = abi,
                    fullUrl = fullUrl,
                    fileName = fileName
                )
            } else null
        }
    }
}
