package org.mozilla.tryfox.install

sealed interface InstallState {
    data object Idle : InstallState
    data object Installing : InstallState
    data class Conflict(val packageName: String) : InstallState
    data object Uninstalling : InstallState
    data class Installed(val packageName: String) : InstallState
    data class Failed(val message: String) : InstallState
}

data class UninstallRequest(
    val operationId: String,
    val packageName: String,
)
