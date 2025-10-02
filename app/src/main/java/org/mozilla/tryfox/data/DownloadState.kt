package org.mozilla.tryfox.data

import java.io.File

/**
 * Represents the various states of a file download operation.
 */
sealed class DownloadState {
    /**
     * Indicates that the download has not yet started.
     */
    data object NotDownloaded : DownloadState()

    /**
     * Indicates that the download is currently in progress.
     * @property progress A float value between 0.0 and 1.0 representing the download progress.
     */
    data class InProgress(
        val progress: Float,
    ) : DownloadState()

    /**
     * Indicates that the download has completed successfully.
     * @property file The [File] object representing the downloaded file.
     */
    data class Downloaded(
        val file: File,
    ) : DownloadState()

    /**
     * Indicates that the download has failed.
     * @property errorMessage An optional string containing a message describing the reason for the failure.
     */
    data class DownloadFailed(
        val errorMessage: String?,
    ) : DownloadState()
}
