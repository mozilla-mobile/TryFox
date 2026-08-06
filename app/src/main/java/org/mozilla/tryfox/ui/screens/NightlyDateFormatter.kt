package org.mozilla.tryfox.ui.screens

import kotlinx.datetime.Clock
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.todayIn

/** Formats a Nightly build timestamp for its date chip on Home. */
internal fun String.formatNightlyBuildDate(
    today: LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault()),
): String {
    val relativeDate = when (rawNightlyBuildDate()) {
        today -> "Today"
        today.minus(1, DateTimeUnit.DAY) -> "Yesterday"
        else -> return formatNightlyBuildTimestamp()
    }
    val time = formatNightlyBuildTimestamp().substringAfter(" ", missingDelimiterValue = "")
    return if (time.isBlank()) relativeDate else "$relativeDate $time"
}

/** Parses the calendar day encoded by an archive Nightly timestamp. */
internal fun String.rawNightlyBuildDate(): LocalDate? {
    val parts = substringBefore(" ").split("-")
    if (parts.size < 3) return null

    return try {
        LocalDate(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    } catch (_: IllegalArgumentException) {
        null
    }
}

internal fun String.formatNightlyBuildTimestamp(): String {
    val parts = split("-")
    return if (parts.size >= 6) {
        "${parts[0]}-${parts[1]}-${parts[2]} ${parts[3]}:${parts[4]}"
    } else {
        this
    }
}
