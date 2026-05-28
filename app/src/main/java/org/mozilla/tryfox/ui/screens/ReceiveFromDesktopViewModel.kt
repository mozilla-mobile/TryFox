package org.mozilla.tryfox.ui.screens

import androidx.lifecycle.ViewModel
import org.mozilla.tryfox.lan.LanReceiveStateRepository

class ReceiveFromDesktopViewModel(
    lanReceiveStateRepository: LanReceiveStateRepository,
) : ViewModel() {
    val state = lanReceiveStateRepository.state
}
