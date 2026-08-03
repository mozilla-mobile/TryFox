package org.mozilla.tryfox.ui.screens

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class PushTimeFormatterTest {

    private val zoneId = ZoneId.of("UTC")
    private val locale = Locale.US
    private val nowMillis = Instant.parse("2026-01-03T12:00:00Z").toEpochMilli()

    @Test
    fun `formats a push from today with a relative label`() {
        assertEquals(
            "Today at 09:05",
            formatRelativePushTime(
                pushTimestampSeconds = Instant.parse("2026-01-03T09:05:45Z").epochSecond,
                nowMillis = nowMillis,
                zoneId = zoneId,
                locale = locale,
            ),
        )
    }

    @Test
    fun `formats a push from yesterday with a relative label`() {
        assertEquals(
            "Yesterday at 09:05",
            formatRelativePushTime(
                pushTimestampSeconds = Instant.parse("2026-01-02T09:05:45Z").epochSecond,
                nowMillis = nowMillis,
                zoneId = zoneId,
                locale = locale,
            ),
        )
    }

    @Test
    fun `formats older pushes with a date`() {
        assertEquals(
            "Jan 1 at 09:05",
            formatRelativePushTime(
                pushTimestampSeconds = Instant.parse("2026-01-01T09:05:45Z").epochSecond,
                nowMillis = nowMillis,
                zoneId = zoneId,
                locale = locale,
            ),
        )
    }
}
