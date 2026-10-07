package org.mozilla.tryfox.data

import kotlinx.datetime.LocalDate
import org.mozilla.tryfox.model.MozillaArchiveApk
import org.mozilla.tryfox.util.UNIVERSAL_ABI

class MozillaArchiveHtmlParser {

    private companion object {
        // [<yyyy-MM-dd-HH-mm-ss>-]<app>-<version>-android[-<abi>]
        val APK_DIRECTORY_PATTERN =
            Regex("^(?:(\\d{4}-\\d{2}-\\d{2}-\\d{2}-\\d{2}-\\d{2})-)?(.+?)-(\\d+\\.\\d+(?:\\.\\d+)?(?:[ab]\\d+)?)-android(?:-(.+))?$")
    }

    fun parseNightlyBuildsFromHtml(
        html: String,
        archiveUrl: String,
        date: LocalDate?,
    ): List<MozillaArchiveApk> {
        val builds = parseApkDirectoriesFromHtml(html).filter { it.timestamp != null }
        val day = date?.toString() ?: builds.maxOfOrNull { it.timestamp!!.take(10) } ?: return emptyList()
        return builds.filter { it.timestamp!!.startsWith(day) }.map { it.toApk(archiveUrl) }
    }

    /** Parses the APK build directories in an archive listing; see [ArchiveApkDirectory]. */
    fun parseApkDirectoriesFromHtml(html: String): List<ArchiveApkDirectory> =
        parseDirectoryNamesFromHtml(html).mapNotNull(::parseApkDirectory)

    private fun parseApkDirectory(name: String): ArchiveApkDirectory? {
        val match = APK_DIRECTORY_PATTERN.matchEntire(name) ?: return null
        val (timestamp, appName, version, abi) = match.destructured
        return ArchiveApkDirectory(
            directory = "$name/",
            timestamp = timestamp.ifEmpty { null },
            appName = appName,
            version = version,
            abi = abi.ifEmpty { UNIVERSAL_ABI },
        )
    }

    fun parseFenixReleasesFromHtml(html: String, releaseType: ReleaseType = ReleaseType.Beta): String {
        return parseFenixReleaseVersionsFromHtml(html, releaseType).firstOrNull() ?: ""
    }

    fun parseFenixReleaseVersionsFromHtml(html: String, releaseType: ReleaseType = ReleaseType.Beta): List<String> =
        releaseVersionsFromDirectories(parseDirectoryNamesFromHtml(html), releaseType)

    /** Returns candidate base versions, without their `-candidates` directory suffix. */
    fun parseFenixCandidateVersionsFromHtml(html: String, releaseType: ReleaseType): List<String> =
        candidateBaseVersionsFromDirectories(parseDirectoryNamesFromHtml(html), releaseType)

    /**
     * Extracts the directory names (without trailing `/`) from an archive index page. This is the
     * channel-independent form of a listing, so one fetch can serve both Release and Beta, and
     * it's what [parseApkDirectoriesFromHtml] reads build directories from.
     */
    fun parseDirectoryNamesFromHtml(html: String): List<String> {
        val directoryPattern = Regex("<a href=\"[^\"]+\">([^<]+)/</a>")
        return directoryPattern.firstGroupOfAll(html)
    }

    /** Filters a `releases/` listing to the versions of [releaseType], newest first. */
    fun releaseVersionsFromDirectories(directories: List<String>, releaseType: ReleaseType): List<String> {
        val releasePattern = Regex("[0-9.]+[a-zA-Z0-9.-]*")
        val rawReleaseStrings = directories.filter { it.matches(releasePattern) }

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

    /** Filters a `candidates/` listing to the base versions of [releaseType], newest first. */
    fun candidateBaseVersionsFromDirectories(directories: List<String>, releaseType: ReleaseType): List<String> {
        return directories
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
            .reversed()
    }

    /** Extracts numeric build directories such as `build1/` and `build12/`. */
    fun parseCandidateBuildNumbersFromHtml(html: String): List<Int> {
        val directoryPattern = Regex("<a href=\"[^\"]+\">build(\\d+)/</a>")
        return directoryPattern.firstGroupOfAll(html)
            .mapNotNull { it.toIntOrNull() }
            .distinct()
            .sortedDescending()
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

    /**
     * The first capture group of every match. Kotlin's [Regex.findAll] creates a new Matcher per
     * match, and on Android each one copies the whole input, which makes scanning a large listing
     * cost matches x length (about 200 ms for `fenix/releases/`). One Matcher avoids that.
     */
    private fun Regex.firstGroupOfAll(input: CharSequence): List<String> {
        val matcher = toPattern().matcher(input)
        return buildList { while (matcher.find()) add(matcher.group(1)) }
    }
}
