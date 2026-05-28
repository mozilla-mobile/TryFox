package org.mozilla.tryfox.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.launch
import org.mozilla.tryfox.lan.LanMessageHistoryRepository

class ReceiveMessageHistoryViewModel(
    private val messageHistoryRepository: LanMessageHistoryRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val history = messageHistoryRepository.history

    init {
        viewModelScope.launch(ioDispatcher) {
            messageHistoryRepository.refresh()
        }
    }
}
