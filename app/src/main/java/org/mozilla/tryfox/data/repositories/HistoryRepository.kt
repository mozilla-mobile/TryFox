package org.mozilla.tryfox.data.repositories

import kotlinx.coroutines.flow.StateFlow
import org.mozilla.tryfox.data.TreeherderInstallHistoryEntry

interface HistoryRepository {
    val historyEntries: StateFlow<List<TreeherderInstallHistoryEntry>>

    suspend fun refresh()

    suspend fun upsertHistoryEntry(entry: TreeherderInstallHistoryEntry)

    suspend fun delete(uniqueKey: String)
}
