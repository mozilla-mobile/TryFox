package org.mozilla.tryfox.data

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.util.Version
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
        val releasePattern = Regex("<a href=\"[^\"]+\">([0-9.]+[a-zA-Z0-9.-]*)/</a>")
        val rawReleaseStrings = releasePattern.findAll(html)
            .mapNotNull { it.groups[1]?.value }
            .toList()

        return when (releaseType) {
            ReleaseType.Beta -> {
                // Filter for beta versions only (containing 'b')
                val betaReleases = rawReleaseStrings.filter { version ->
                    version.contains(Regex("[ab]\\d+"))
                }
                betaReleases.maxWithOrNull(::compareReleaseVersions) ?: ""
            }
            ReleaseType.Release -> {
                // Filter for stable releases matching pattern: D+.D+(.D+)?
                // Exclude beta versions (containing 'b', 'beta', 'a', 'alpha', etc.)
                val stableReleases = rawReleaseStrings.filter { version ->
                    // Check if it matches the pattern D+.D+(.D+)? and has no pre-release identifier
                    val isBeta = version.contains(Regex("[ab]\\d+|beta|alpha|rc"))
                    !isBeta && version.matches(Regex("\\d+\\.\\d+(\\.\\d+)?"))
                }
                
                // Use Version comparison to find the latest stable release
                stableReleases.mapNotNull { Version.from(it) }
                    .maxOrNull()?.toString() ?: ""
            }
        }
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
