package org.mozilla.tryfox.data

import java.io.File

sealed class DownloadState {
    object NotDownloaded : DownloadState()
    data class InProgress(val progress: Float, val isIndeterminate: Boolean = false) : DownloadState()
    data class Downloaded(val file: File) : DownloadState()
    data class DownloadFailed(val message: String?) : DownloadState()
}
