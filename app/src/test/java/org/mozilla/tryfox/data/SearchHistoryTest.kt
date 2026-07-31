package org.mozilla.tryfox.data

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class SearchHistoryTest {

    @Test
    fun `records newest first, deduplicates normalized project and query, and limits entries`() {
        val initialEntries = (1..15).map { index ->
            SearchHistoryEntry("try", "revision-$index", SearchHistoryQueryType.REVISION, index.toLong())
        }

        val updatedEntries = SearchHistory.record(
            entries = initialEntries,
            entry = SearchHistoryEntry("TRY", " Revision-5 ", SearchHistoryQueryType.REVISION, 20L),
        )

        assertEquals(15, updatedEntries.size)
        assertEquals("Revision-5", updatedEntries.first().query)
        assertEquals("revision-15", updatedEntries.last().query)
    }

    @Test
    fun `places the latest email before newer revision entries`() {
        val entries = listOf(
            SearchHistoryEntry("try", "revision", SearchHistoryQueryType.REVISION, 30L),
            SearchHistoryEntry("mozilla-central", "older@mozilla.org", SearchHistoryQueryType.EMAIL, 20L),
            SearchHistoryEntry("try", "old-revision", SearchHistoryQueryType.REVISION, 10L),
        )

        assertEquals(
            listOf("older@mozilla.org", "revision", "old-revision"),
            SearchHistory.displayOrder(entries).map(SearchHistoryEntry::query),
        )
        assertEquals("older@mozilla.org", SearchHistory.latestEmail(entries))
    }

    @Test
    fun `creates a legacy email entry only when history is empty`() {
        val legacyEntry = SearchHistory.legacyEmailEntry("person@mozilla.org")

        assertEquals("person@mozilla.org", legacyEntry?.query)
        assertEquals(SearchHistoryQueryType.EMAIL, legacyEntry?.queryType)
        assertEquals(null, SearchHistory.legacyEmailEntry(" "))
    }
}
