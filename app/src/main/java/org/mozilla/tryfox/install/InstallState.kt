package org.mozilla.tryfox.install

sealed interface InstallState {
    data object Idle : InstallState
    data object Installing : InstallState
    data class Conflict(
        val apps: List<ConflictApp>,
        val reason: ConflictReason,
    ) : InstallState
    data object Uninstalling : InstallState
    data class Installed(val packageName: String) : InstallState
    data class Failed(val message: String) : InstallState
}

data class ConflictApp(
    val label: String,
    val packageName: String,
)

enum class ConflictReason {
    INCOMPATIBLE_OR_NEWER,
    SHARED_USER_SIGNATURE,
}

data class UninstallRequest(
    val operationId: String,
    val packageName: String,
)
