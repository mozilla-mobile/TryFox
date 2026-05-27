package org.mozilla.tryfox.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.tryfox.data.repositories.HistoryRepository

class FakeHistoryRepository : HistoryRepository {
    private val _historyEntries = MutableStateFlow<List<TreeherderInstallHistoryEntry>>(emptyList())
    override val historyEntries: StateFlow<List<TreeherderInstallHistoryEntry>> = _historyEntries.asStateFlow()

    val recordedEntries = mutableListOf<TreeherderInstallHistoryEntry>()

    override suspend fun refresh() = Unit

    override suspend fun recordInstallerLaunch(entry: TreeherderInstallHistoryEntry) {
        recordedEntries.removeAll { it.uniqueKey == entry.uniqueKey }
        recordedEntries.add(entry)
        _historyEntries.value = recordedEntries.sortedByDescending { it.lastInstallerLaunchTimestamp }
    }

    override suspend fun delete(uniqueKey: String) {
        recordedEntries.removeAll { it.uniqueKey == uniqueKey }
        _historyEntries.value = recordedEntries.sortedByDescending { it.lastInstallerLaunchTimestamp }
    }

    fun setEntries(entries: List<TreeherderInstallHistoryEntry>) {
        recordedEntries.clear()
        recordedEntries.addAll(entries)
        _historyEntries.value = entries
    }
}
