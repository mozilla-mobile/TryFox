package org.mozilla.tryfox.util

import java.util.regex.Pattern

data class Version(
    val fullStringVersion: String,
    val major: Int,
    val minor: Int,
    val patch: Int,
    val preRelease: String? = null,
) : Comparable<Version> {

    private fun parsePreReleaseParts(preReleaseString: String?): List<Any> {
        return preReleaseString?.split('.')?.map { part ->
            part.toIntOrNull() ?: part // Parse to Int if numeric, otherwise keep as String
        } ?: emptyList()
    }

    override fun compareTo(other: Version): Int {
        // Compare major, minor, patch
        if (this.major != other.major) return this.major.compareTo(other.major)
        if (this.minor != other.minor) return this.minor.compareTo(other.minor)
        if (this.patch != other.patch) return this.patch.compareTo(other.patch)

        // If major, minor, patch are equal, compare pre-release identifiers
        val thisPreReleaseParts = parsePreReleaseParts(this.preRelease)
        val otherPreReleaseParts = parsePreReleaseParts(other.preRelease)

        if (thisPreReleaseParts.isEmpty() && otherPreReleaseParts.isEmpty()) {
            return 0 // Both are stable, or both have no pre-release part
        }
        if (thisPreReleaseParts.isEmpty()) {
            return 1 // This is stable, other is pre-release -> this is greater
        }
        if (otherPreReleaseParts.isEmpty()) {
            return -1 // Other is stable, this is pre-release -> this is smaller
        }

        // Both have pre-release parts, compare them
        val minSize = minOf(thisPreReleaseParts.size, otherPreReleaseParts.size)
        for (i in 0 until minSize) {
            val thisPart = thisPreReleaseParts[i]
            val otherPart = otherPreReleaseParts[i]

            val comparison = when {
                thisPart is Int && otherPart is Int -> thisPart.compareTo(otherPart)
                thisPart is String && otherPart is String -> thisPart.compareTo(otherPart)
                thisPart is Int && otherPart is String -> -1 // Numeric has lower precedence than non-numeric
                thisPart is String && otherPart is Int -> 1 // Non-numeric has higher precedence than numeric
                else -> 0 // Should not happen with current parsing
            }
            if (comparison != 0) return comparison
        }

        // If all common parts are equal, the one with more pre-release identifiers is greater
        return thisPreReleaseParts.size.compareTo(otherPreReleaseParts.size)
    }

    override fun toString(): String = fullStringVersion

    companion object {
        // Regex to capture major, minor, patch, and an optional pre-release identifier
        private val VERSION_REGEX = Pattern.compile("^v?(\\d+)\\.(\\d+)\\.(\\d+)(?:-([0-9A-Za-z.-]+))?$")

        fun from(versionString: String): Version? {
            val matcher = VERSION_REGEX.matcher(versionString)
            return if (matcher.find()) {
                val major = matcher.group(1)?.toIntOrNull() ?: 0
                val minor = matcher.group(2)?.toIntOrNull() ?: 0
                val patch = matcher.group(3)?.toIntOrNull() ?: 0
                val preRelease = matcher.group(4) // This will be null if no pre-release part

                Version(
                    fullStringVersion = versionString,
                    major = major,
                    minor = minor,
                    patch = patch,
                    preRelease = preRelease,
                )
            } else {
                null
            }
        }
    }
}
