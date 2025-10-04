package org.mozilla.tryfox.util

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime

@OptIn(FormatStringsInDatetimeFormats::class)
fun parseDateToMillis(dateString: String): Long {
    if (dateString.isBlank()) return 0L

    val patterns = listOf("yyyy-MM-dd HH:mm", "yyyy-MM-dd-HH-mm-ss")
    for (pattern in patterns) {
        try {
            val format = LocalDateTime.Format { byUnicodePattern(pattern) }
            return format.parse(dateString).toInstant(TimeZone.currentSystemDefault()).toEpochMilliseconds()
        } catch (_: Exception) {
            // continue
        }
    }
    try {
        val format = LocalDate.Format { byUnicodePattern("yyyy-MM-dd") }
        return format.parse(dateString).atStartOfDayIn(TimeZone.currentSystemDefault()).toEpochMilliseconds()
    } catch (_: Exception) {
        // continue
    }

    return 0L
}

fun parseDateToLocalDate(dateString: String): LocalDate? {
    if (dateString.isBlank()) return null
    return try {
        Instant.fromEpochMilliseconds(parseDateToMillis(dateString)).toLocalDateTime(TimeZone.currentSystemDefault()).date
    } catch (e: Exception) {
        null
    }
}
