package org.mozilla.tryfox.download.model

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadStatus {
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED,
}

@Serializable
data class PersistedDownloadState(
    val uniqueKey: String,
    val downloadUrl: String,
    val outputPath: String,
    val appName: String,
    val fileName: String,
    val cacheRelativePath: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = -1L,
    val errorMessage: String? = null,
    val workId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = createdAt,
) {
    val progress: Float?
        get() = if (totalBytes > 0L) bytesDownloaded.toFloat() / totalBytes.toFloat() else null

    val isTerminal: Boolean
        get() = status == DownloadStatus.SUCCEEDED || status == DownloadStatus.FAILED || status == DownloadStatus.CANCELED
}
