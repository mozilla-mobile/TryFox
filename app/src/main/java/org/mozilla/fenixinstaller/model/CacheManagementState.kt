package org.mozilla.fenixinstaller.model

sealed class CacheManagementState {
    data object IdleNonEmpty : CacheManagementState()
    data object IdleEmpty : CacheManagementState()
    data object Clearing : CacheManagementState()
}
