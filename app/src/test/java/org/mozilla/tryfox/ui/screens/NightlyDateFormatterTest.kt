package org.mozilla.tryfox.ui.screens

import kotlinx.datetime.LocalDate
import kotlinx.datetime.Instant
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NightlyDateFormatterTest {

    private val today = LocalDate(2026, 8, 5)

    @Test
    fun `formats a build from today by replacing only its date`() {
        assertEquals(
            "Today 09:17",
            "2026-08-05-09-17-32".formatNightlyBuildDate(today),
        )
    }

    @Test
    fun `formats a build from yesterday as Yesterday across a month boundary`() {
        assertEquals(
            "Yesterday 09:17",
            "2026-07-31-09-17-32".formatNightlyBuildDate(LocalDate(2026, 8, 1)),
        )
    }

    @Test
    fun `formats an older build with its normal timestamp`() {
        assertEquals(
            "2026-08-03 09:17",
            "2026-08-03-09-17-32".formatNightlyBuildDate(today),
        )
    }

    @Test
    fun `preserves an unrecognised date`() {
        assertEquals(
            "unknown-date",
            "unknown-date".formatNightlyBuildDate(today),
        )
    }

    @Test
    fun `parses a build date independently of its display label`() {
        assertEquals(today, "2026-08-05-09-17-32".rawNightlyBuildDate())
        assertEquals(LocalDate(2026, 8, 4), "2026-08-04-09-17-32".rawNightlyBuildDate())
    }

    @Test
    fun `uses UTC midnight for the calendar selected date`() {
        val selectedDate = LocalDate(2026, 8, 5)

        assertEquals(
            Instant.parse("2026-08-05T00:00:00Z").toEpochMilliseconds(),
            selectedDate.toDatePickerSelectionMillis(),
        )
        assertEquals(selectedDate, datePickerSelectionDate(selectedDate.toDatePickerSelectionMillis()))
    }
}
