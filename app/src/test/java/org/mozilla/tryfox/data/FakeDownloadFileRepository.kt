package org.mozilla.tryfox.data

import kotlinx.coroutines.delay
import java.io.File

class FakeDownloadFileRepository(
    private val simulateNetworkError: Boolean = false,
    private val networkErrorMessage: String = "Fake network error",
    private val downloadProgressDelayMillis: Long = 100L,
) : DownloadFileRepository {

    var downloadFileCalled = false
    var downloadFileResult: NetworkResult<File> = NetworkResult.Success(File("fake_path"))

    override suspend fun downloadFile(
        downloadUrl: String,
        outputFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit,
    ): NetworkResult<File> {
        downloadFileCalled = true

        if (simulateNetworkError) {
            return NetworkResult.Error(networkErrorMessage, null)
        }

        val totalBytes = 10_000_000L

        onProgress(0, totalBytes)
        delay(downloadProgressDelayMillis)

        onProgress(totalBytes / 2, totalBytes)
        delay(downloadProgressDelayMillis)

        outputFile.parentFile?.mkdirs()
        try {
            if (!outputFile.exists()) {
                outputFile.createNewFile()
            }
            outputFile.writeText("This is a fake downloaded artifact: ${outputFile.name} from $downloadUrl")
        } catch (e: Exception) {
            return NetworkResult.Error("Failed to create fake artifact file: ${e.message}", e)
        }

        onProgress(totalBytes, totalBytes)
        delay(downloadProgressDelayMillis)

        return NetworkResult.Success(outputFile)
    }
}
