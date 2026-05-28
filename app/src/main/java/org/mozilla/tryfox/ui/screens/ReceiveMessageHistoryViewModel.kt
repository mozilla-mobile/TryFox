package org.mozilla.tryfox.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import org.mozilla.tryfox.lan.LanMessageHistoryRepository
import org.mozilla.tryfox.lan.LanReceivedMessage

class ReceiveMessageHistoryViewModel(
    private val messageHistoryRepository: LanMessageHistoryRepository,
    private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {
    val history: StateFlow<List<LanReceivedMessage>> = messageHistoryRepository.history

    init {
        viewModelScope.launch(ioDispatcher) {
            messageHistoryRepository.refresh()
        }
    }

    fun delete(message: LanReceivedMessage) {
        viewModelScope.launch(ioDispatcher) {
            messageHistoryRepository.delete(message.id)
        }
    }
}
