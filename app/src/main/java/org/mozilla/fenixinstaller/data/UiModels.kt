package org.mozilla.fenixinstaller.data

import java.io.File

// --- Download State ---
sealed class DownloadState {
    data object NotDownloaded : DownloadState()
    data class InProgress(val progress: Float) : DownloadState()
    data class Downloaded(val file: File) : DownloadState()
    data class DownloadFailed(val errorMessage: String?) : DownloadState()
}

// --- Artifact UI Model ---
data class ArtifactUiModel(
    val originalArtifact: Artifact, // To access name, expires, abi, getDownloadUrl
    val taskId: String,
    val isCompatibleAbi: Boolean,
    var downloadState: DownloadState // var to allow ViewModel to update it
) {
    // Convenience accessors
    val name: String get() = originalArtifact.name
    val expires: String get() = originalArtifact.expires
    val abi: String? get() = originalArtifact.abi
    fun getDownloadUrl(): String = originalArtifact.getDownloadUrl(taskId)
    val uniqueKey: String get() = "$taskId/${name.substringAfterLast('/')}" // For map keys / download tracking
}

// --- Job Details UI Model ---
// Initially, this might be very similar to JobDetails, but it provides a layer for UI-specific needs.
data class JobDetailsUiModel(
    val appName: String,
    val jobName: String,
    val jobSymbol: String,
    val taskId: String,
    val isSignedBuild: Boolean, // From original JobDetails
    val isTest: Boolean,        // From original JobDetails
    var artifacts: List<ArtifactUiModel> = emptyList() // To hold associated artifacts directly
)
