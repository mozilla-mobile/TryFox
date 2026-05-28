package org.mozilla.tryfox.lan

import kotlinx.coroutines.flow.StateFlow

interface LanMessageHistoryRepository {
    val history: StateFlow<List<LanReceivedMessage>>

    suspend fun refresh()

    suspend fun record(message: LanReceivedMessage): LanReceivedMessage

    suspend fun replaceAll(messages: List<LanReceivedMessage>): List<LanReceivedMessage>

    suspend fun delete(id: Long)
}
