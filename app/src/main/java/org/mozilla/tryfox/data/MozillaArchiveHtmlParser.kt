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

    fun parseFenixReleasesFromHtml(html: String, releaseType: ReleaseType = ReleaseType.Beta): String {
        return parseFenixReleaseVersionsFromHtml(html, releaseType).firstOrNull() ?: ""
    }

    fun parseFenixReleaseVersionsFromHtml(html: String, releaseType: ReleaseType = ReleaseType.Beta): List<String> {
        val releasePattern = Regex("<a href=\"[^\"]+\">([0-9.]+[a-zA-Z0-9.-]*)/</a>")
        val rawReleaseStrings = releasePattern.findAll(html)
            .mapNotNull { it.groups[1]?.value }
            .toList()

        return when (releaseType) {
            ReleaseType.Beta -> {
                rawReleaseStrings.filter { version ->
                    version.contains(Regex("[ab]\\d+"))
                }.sortedWith(::compareReleaseVersions).reversed()
            }
            ReleaseType.Release -> {
                rawReleaseStrings.filter(::isStableReleaseVersion)
                    .sortedWith(::compareReleaseVersions)
                    .reversed()
            }
        }
    }

    /** Returns candidate base versions, without their `-candidates` directory suffix. */
    fun parseFenixCandidateVersionsFromHtml(html: String, releaseType: ReleaseType): List<String> {
        val directoryPattern = Regex("<a href=\"[^\"]+\">([^<]+/)</a>")
        return directoryPattern.findAll(html)
            .mapNotNull { it.groups[1]?.value?.removeSuffix("/") }
            .filter { it.endsWith("-candidates") }
            .map { it.removeSuffix("-candidates") }
            .filter { candidate ->
                when (releaseType) {
                    ReleaseType.Beta -> candidate.matches(Regex("\\d+\\.\\d+b\\d+"))
                    ReleaseType.Release -> isStableReleaseVersion(candidate)
                }
            }
            .distinct()
            .sortedWith(::compareReleaseVersions)
            .toList()
            .reversed()
    }

    /** Extracts numeric build directories such as `build1/` and `build12/`. */
    fun parseCandidateBuildNumbersFromHtml(html: String): List<Int> {
        val directoryPattern = Regex("<a href=\"[^\"]+\">build(\\d+)/</a>")
        return directoryPattern.findAll(html)
            .mapNotNull { it.groups[1]?.value?.toIntOrNull() }
            .distinct()
            .sortedDescending()
            .toList()
    }

    fun parseFenixReleaseAbisFromHtml(html: String, appName: String): List<String> {
        // Pattern: {appName}-D+.D+(.D+)?-android-ABI/ or {appName}-D+.D+(.D+)?-android/
        // Also supports beta/alpha markers: {appName}-D+.D+(.D+)?[ab]D+-android-ABI/
        val htmlPattern = Regex("<td>Dir</td>\\s*<td><a href=\"[^\"]*\">([^<]+/)</a></td>")
        val rawBuildStrings = htmlPattern.findAll(html)
            .mapNotNull { it.groups[1]?.value }
            .filter { it != "../" }
            .toList()

        val abis = mutableListOf<String>()

        for (buildString in rawBuildStrings) {
            // Pattern: {appName}-D+.D+(.D+)?[ab]D+-android-ABI/ or {appName}-D+.D+(.D+)?-android/
            // Also supports: {appName}-D+.D+(.D+)?-android-ABI/ (stable releases)
            // Examples:
            // - fenix-145.0-android-arm64-v8a/ (stable)
            // - fenix-145.0-android/ (stable universal)
            // - fenix-146.0b5-android-arm64-v8a/ (beta)
            // - fenix-146.0b5-android/ (beta universal)
            val pattern = Regex("^$appName-\\d+\\.\\d+(?:\\.\\d+)?(?:[ab]\\d+)?-android(?:-(.+?))?/$")
            val matchResult = pattern.find(buildString)

            if (matchResult != null) {
                val abi = matchResult.groups[1]?.value
                abis.add(abi ?: "universal")
            }
        }
        return abis
    }

    internal fun compareReleaseVersions(version1: String, version2: String): Int {
        val rc1 = parseCandidateDisplayVersion(version1)
        val rc2 = parseCandidateDisplayVersion(version2)
        if (rc1 != null || rc2 != null) {
            val base1 = rc1?.first ?: version1
            val base2 = rc2?.first ?: version2
            val baseComparison = compareReleaseVersionsWithoutRc(base1, base2)
            if (baseComparison != 0) return baseComparison
            return (rc1?.second ?: Int.MAX_VALUE).compareTo(rc2?.second ?: Int.MAX_VALUE)
        }
        return compareReleaseVersionsWithoutRc(version1, version2)
    }

    private fun compareReleaseVersionsWithoutRc(version1: String, version2: String): Int {
        val parts1 = version1.split(Regex("[.b-]")).mapNotNull { it.toIntOrNull() }
        val parts2 = version2.split(Regex("[.b-]")).mapNotNull { it.toIntOrNull() }

        val maxParts = maxOf(parts1.size, parts2.size)
        for (i in 0 until maxParts) {
            val part1 = parts1.getOrElse(i) { 0 }
            val part2 = parts2.getOrElse(i) { 0 }
            if (part1 != part2) {
                return part1.compareTo(part2)
            }
        }
        return 0
    }

    private fun parseCandidateDisplayVersion(version: String): Pair<String, Int>? {
        val match = Regex("^(.+)-RC(\\d+)$").matchEntire(version) ?: return null
        return match.groupValues[1] to match.groupValues[2].toInt()
    }

    private fun isStableReleaseVersion(version: String): Boolean {
        val isPreRelease = version.contains(Regex("[ab]\\d+|beta|alpha|rc", RegexOption.IGNORE_CASE))
        return !isPreRelease && version.matches(Regex("\\d+\\.\\d+(\\.\\d+)?"))
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
