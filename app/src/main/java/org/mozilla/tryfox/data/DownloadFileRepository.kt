package org.mozilla.tryfox.data

import java.io.File

/**
 * Interface for a repository responsible for downloading files.
 */
interface DownloadFileRepository {
    /**
     * Downloads a file from the given URL to the specified output file, reporting progress.
     *
     * @param downloadUrl The URL of the file to download.
     * @param outputFile The file where the downloaded content will be saved.
     * @param onProgress A callback function to report download progress (bytesDownloaded, totalBytes).
     * @return A [NetworkResult] indicating success with the downloaded [File] or an [NetworkResult.Error] on failure.
     */
    suspend fun downloadFile(downloadUrl: String, outputFile: File, onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit): NetworkResult<File>
}
