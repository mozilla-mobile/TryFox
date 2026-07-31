package org.mozilla.tryfox.data

import kotlinx.serialization.Serializable

@Serializable
data class SearchHistoryEntry(
    val project: String,
    val query: String,
    val queryType: SearchHistoryQueryType,
    val searchedAt: Long,
)

@Serializable
enum class SearchHistoryQueryType {
    EMAIL,
    REVISION,
}

object SearchHistory {
    const val MAX_ENTRIES = 15

    fun record(entries: List<SearchHistoryEntry>, entry: SearchHistoryEntry): List<SearchHistoryEntry> {
        val normalizedEntry = entry.copy(project = entry.project.trim(), query = entry.query.trim())
        return listOf(normalizedEntry) + entries.filterNot { existing ->
            existing.project.equals(normalizedEntry.project, ignoreCase = true) &&
                existing.query.equals(normalizedEntry.query, ignoreCase = true)
        }.take(MAX_ENTRIES - 1)
    }

    fun displayOrder(entries: List<SearchHistoryEntry>): List<SearchHistoryEntry> {
        val newestEmail = entries
            .asSequence()
            .filter { it.queryType == SearchHistoryQueryType.EMAIL }
            .maxByOrNull(SearchHistoryEntry::searchedAt)
        return listOfNotNull(newestEmail) + entries
            .filterNot { it == newestEmail }
            .sortedByDescending(SearchHistoryEntry::searchedAt)
    }

    fun latestEmail(entries: List<SearchHistoryEntry>): String =
        entries.filter { it.queryType == SearchHistoryQueryType.EMAIL }
            .maxByOrNull(SearchHistoryEntry::searchedAt)
            ?.query
            .orEmpty()

    fun legacyEmailEntry(email: String): SearchHistoryEntry? =
        email.trim().takeIf(String::isNotBlank)?.let {
            SearchHistoryEntry(
                project = "try",
                query = it,
                queryType = SearchHistoryQueryType.EMAIL,
                searchedAt = 0L,
            )
        }
}
