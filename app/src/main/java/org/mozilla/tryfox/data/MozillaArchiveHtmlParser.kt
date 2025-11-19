package org.mozilla.tryfox.data

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.model.MozillaArchiveApk
import java.util.regex.Pattern

class MozillaArchiveHtmlParser {

    fun parseNightlyBuildsFromHtml(
        html: String,
        archiveUrl: String,
        date: LocalDate?,
    ): List<MozillaArchiveApk> {
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

        return buildsForDate.mapNotNull { buildString ->
            parseBuildString(buildString, archiveUrl)
        }
    }

    private fun parseBuildString(buildString: String, archiveUrl: String): MozillaArchiveApk? {
        val apkPattern =
            Pattern.compile("^(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})-(.*?)-([^-]+)-android-(.*?)/$")
        val matcher = apkPattern.matcher(buildString)
        if (matcher.matches()) {
            val rawDate = matcher.group(1) ?: ""
            val appNameResult = matcher.group(2) ?: ""
            val version = matcher.group(3) ?: ""
            val abi = matcher.group(4) ?: ""

            val fileName = "$appNameResult-$version.multi.android-$abi.apk"
            val fullUrl = "${archiveUrl}${buildString}$fileName"

            return MozillaArchiveApk(
                originalString = buildString,
                rawDateString = rawDate,
                appName = appNameResult,
                version = version,
                abiName = abi,
                fullUrl = fullUrl,
                fileName = fileName,
            )
        }

        return null

    }
}
