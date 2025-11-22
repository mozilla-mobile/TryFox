package org.mozilla.tryfox.data.repositories

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.network.DownloadApiService
import java.io.File
import java.io.FileOutputStream

/**
 * Default implementation of [DownloadFileRepository] for downloading files.
 */
class DefaultDownloadFileRepository(
    private val downloadApiService: DownloadApiService,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : DownloadFileRepository {
    override suspend fun downloadFile(downloadUrl: String, outputFile: File, onProgress: (Long, Long) -> Unit): NetworkResult<File> {
        return withContext(ioDispatcher) {
            try {
                val response = downloadApiService.downloadFile(downloadUrl)
                val body = response.byteStream()
                val totalBytes = response.contentLength()
                var bytesCopied: Long = 0

                body.use { inputStream ->
                    FileOutputStream(outputFile).use { outputStream ->
                        val buffer = ByteArray(4 * 1024)
                        var read: Int
                        while (inputStream.read(buffer).also { read = it } != -1) {
                            outputStream.write(buffer, 0, read)
                            bytesCopied += read
                            onProgress(bytesCopied, totalBytes)
                        }
                    }
                }
                NetworkResult.Success(outputFile)
            } catch (e: Exception) {
                NetworkResult.Error("Failed to download file: ${e.message}", e)
            }
        }
    }
}
