package org.mozilla.tryfox.lan

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LanReceiveStateRepository {
    private val _state = MutableStateFlow(LanReceiveSessionState())
    val state: StateFlow<LanReceiveSessionState> = _state.asStateFlow()

    fun update(transform: (LanReceiveSessionState) -> LanReceiveSessionState) {
        _state.value = transform(_state.value)
    }

    fun set(value: LanReceiveSessionState) {
        _state.value = value
    }
}
