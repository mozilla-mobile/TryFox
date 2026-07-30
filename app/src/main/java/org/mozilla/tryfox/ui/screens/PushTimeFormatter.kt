package org.mozilla.tryfox.ui.screens

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

internal fun formatRelativePushTime(
    pushTimestampSeconds: Long,
    nowMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
    locale: Locale = Locale.getDefault(),
): String {
    val pushTime = Instant.ofEpochSecond(pushTimestampSeconds).atZone(zoneId)
    val today = Instant.ofEpochMilli(nowMillis).atZone(zoneId).toLocalDate()
    val date = pushTime.toLocalDate()
    val time = pushTime.toLocalTime().truncatedTo(ChronoUnit.MINUTES)
        .format(DateTimeFormatter.ofPattern("HH:mm", locale))
    return when {
        date == today -> "Today at $time"
        date == today.minusDays(1) -> "Yesterday at $time"
        else -> "${date.format(DateTimeFormatter.ofPattern("MMM d", locale))} at $time"
    }
}
