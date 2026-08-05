package org.mozilla.tryfox.util

private val reviewerToken = "(?:#[A-Za-z0-9_-]+|[A-Za-z0-9_.-]+)"
private val trailingReviewerDirective = Regex(
    "\\s+r[=?]\\s*$reviewerToken(?:\\s*,\\s*$reviewerToken)*!?\\s*$",
    RegexOption.IGNORE_CASE,
)

/** Removes trailing Phabricator reviewer metadata from a commit subject for display. */
internal fun String.withoutTrailingReviewerDirective(): String {
    val subjectEnd = indexOfFirst { it == '\n' || it == '\r' }.let { index ->
        if (index == -1) length else index
    }
    val subject = substring(0, subjectEnd)
    val description = substring(subjectEnd)
    return trailingReviewerDirective.replace(subject, "").trimEnd() + description
}
