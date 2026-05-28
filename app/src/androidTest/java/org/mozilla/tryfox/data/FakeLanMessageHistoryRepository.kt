package org.mozilla.tryfox.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.tryfox.lan.LanMessageHistoryRepository
import org.mozilla.tryfox.lan.LanReceivedMessage

class FakeLanMessageHistoryRepository : LanMessageHistoryRepository {
    private val _history = MutableStateFlow<List<LanReceivedMessage>>(emptyList())
    override val history: StateFlow<List<LanReceivedMessage>> = _history.asStateFlow()

    override suspend fun refresh() = Unit

    override suspend fun record(message: LanReceivedMessage): LanReceivedMessage {
        val storedMessage = message.copy(id = (_history.value.size + 1).toLong())
        _history.value = listOf(storedMessage) + _history.value
        return storedMessage
    }

    override suspend fun replaceAll(messages: List<LanReceivedMessage>): List<LanReceivedMessage> {
        val storedMessages = messages.mapIndexed { index, message ->
            message.copy(id = (_history.value.size + index + 1).toLong())
        }
        _history.value = storedMessages + _history.value
        return storedMessages
    }

    override suspend fun delete(id: Long) {
        _history.value = _history.value.filterNot { it.id == id }
    }

    fun setHistory(messages: List<LanReceivedMessage>) {
        _history.value = messages
    }
}
