package org.mozilla.tryfox.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import org.mozilla.tryfox.data.SearchHistory
import org.mozilla.tryfox.data.SearchHistoryEntry
import org.mozilla.tryfox.data.SearchHistoryQueryType
import org.mozilla.tryfox.data.repositories.UserDataRepository
import org.mozilla.tryfox.lan.LanReceiveIdentity

/**
 * A fake implementation of [org.mozilla.tryfox.data.repositories.UserDataRepository] for testing purposes.
 */
class FakeUserDataRepository : UserDataRepository {

    private val _lastSearchedEmailFlow = MutableStateFlow("")
    override val lastSearchedEmailFlow: Flow<String> = _lastSearchedEmailFlow
    private val _searchHistoryFlow = MutableStateFlow<List<SearchHistoryEntry>>(emptyList())
    override val searchHistoryFlow: Flow<List<SearchHistoryEntry>> = _searchHistoryFlow
    private val _lanReceiveIdentityFlow = MutableStateFlow<LanReceiveIdentity?>(null)
    override val lanReceiveIdentityFlow: Flow<LanReceiveIdentity?> = _lanReceiveIdentityFlow

    override suspend fun saveLastSearchedEmail(email: String) {
        recordSearch("try", email)
    }

    override suspend fun recordSearch(project: String, query: String, searchedAt: Long) {
        val queryType = if ('@' in query) SearchHistoryQueryType.EMAIL else SearchHistoryQueryType.REVISION
        _searchHistoryFlow.value = SearchHistory.record(
            _searchHistoryFlow.value,
            SearchHistoryEntry(project, query, queryType, searchedAt),
        )
        _lastSearchedEmailFlow.value = SearchHistory.latestEmail(_searchHistoryFlow.value)
    }

    override suspend fun saveLanReceiveIdentity(identity: LanReceiveIdentity) {
        _lanReceiveIdentityFlow.value = identity
    }

    // Helper method for tests to clear the stored email if needed
    fun clearLastSearchedEmail() {
        _lastSearchedEmailFlow.value = ""
        _searchHistoryFlow.value = emptyList()
    }
}
