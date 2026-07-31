package org.mozilla.tryfox.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.mozilla.tryfox.data.SearchHistoryEntry
import org.mozilla.tryfox.data.repositories.UserDataRepository

class SearchHistoryViewModel(
    private val userDataRepository: UserDataRepository,
) : ViewModel() {
    val searchHistory: StateFlow<List<SearchHistoryEntry>> = userDataRepository.searchHistoryFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList(),
    )

    fun recordSuccessfulSearch(project: String, query: String) {
        viewModelScope.launch {
            runCatching { userDataRepository.recordSearch(project, query) }
        }
    }
}
