package org.mozilla.tryfox.data

import android.util.Log // Added import
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import logcat.LogPriority
import logcat.logcat
import org.mozilla.tryfox.network.ApiService

class FenixRepository(
    private val treeherderApiService: ApiService
) : IFenixRepository {

    companion object {
        private const val TAG = "FenixRepository"
        const val TREEHERDER_BASE_URL = "https://treeherder.mozilla.org/api/"
        const val TASKCLUSTER_BASE_URL = "https://firefox-ci-tc.services.mozilla.com/api/queue/v1/"
    }

    private suspend fun <T> safeApiCall(apiCall: suspend () -> T): NetworkResult<T> {
        return try {
            NetworkResult.Success(apiCall.invoke())
        } catch (e: Exception) {
            NetworkResult.Error(e.message ?: "Unknown error", e)
        }
    }

    override suspend fun getPushByRevision(
        project: String,
        revision: String
    ): NetworkResult<TreeherderRevisionResponse> {
        return safeApiCall { treeherderApiService.getPushByRevision(project, revision) }
    }

    override suspend fun getPushesByAuthor(author: String): NetworkResult<TreeherderRevisionResponse> {
        return safeApiCall { treeherderApiService.getPushByAuthor(author = author) }
    }

    override suspend fun getJobsForPush(pushId: Int): NetworkResult<TreeherderJobsResponse> {
        return safeApiCall { treeherderApiService.getJobsForPush(pushId) }
    }

    override suspend fun getArtifactsForTask(taskId: String): NetworkResult<ArtifactsResponse> {
        val artifactsUrl = "${TASKCLUSTER_BASE_URL}task/$taskId/runs/0/artifacts"
        return safeApiCall { treeherderApiService.getArtifactsForTask(artifactsUrl) }
    }

    override suspend fun downloadArtifact(
        downloadUrl: String,
        outputFile: File,
        onProgress: (bytesDownloaded: Long, totalBytes: Long) -> Unit
    ): NetworkResult<File> {
        logcat(TAG) { "downloadArtifact called. URL: $downloadUrl, OutputFile: ${outputFile.absolutePath}" }
        return try {
            logcat(LogPriority.DEBUG, TAG) { "Attempting to download file from ApiService: $downloadUrl" }
            val responseBody = treeherderApiService.downloadFile(downloadUrl)
            logcat(LogPriority.DEBUG, TAG) { "Got responseBody. ContentLength: ${responseBody.contentLength()}" }

            outputFile.parentFile?.mkdirs()
            logcat(LogPriority.VERBOSE, TAG) { "Parent directories created/ensured for ${outputFile.absolutePath}" }

            val totalBytes = responseBody.contentLength()
            var bytesDownloaded: Long = 0
            logcat(LogPriority.DEBUG, TAG) { "Starting file write. TotalBytes: $totalBytes" }

            withContext(Dispatchers.IO) {
                logcat(LogPriority.DEBUG, TAG) { "Entered withContext(Dispatchers.IO) for file writing." }
                var inputStream: InputStream? = null
                var outputStream: OutputStream? = null
                try {
                    inputStream = responseBody.byteStream()
                    outputStream = FileOutputStream(outputFile)
                    logcat(LogPriority.VERBOSE, TAG) { "InputStream and OutputStream opened." }
                    val buffer = ByteArray(4 * 1024) // 4KB buffer
                    var read: Int
                    logcat(LogPriority.DEBUG, TAG) { "Starting read/write loop." }
                    while (inputStream.read(buffer).also { read = it } != -1) {
                        outputStream.write(buffer, 0, read)
                        bytesDownloaded += read
                        if (bytesDownloaded == 0L || bytesDownloaded == totalBytes || (bytesDownloaded % (totalBytes / 10).coerceAtLeast(1)) == 0L) {
                            logcat(LogPriority.VERBOSE, TAG) { "Progress: $bytesDownloaded / $totalBytes" }
                        }
                        onProgress(bytesDownloaded, totalBytes)
                    }
                    logcat(LogPriority.DEBUG, TAG) { "Finished read/write loop. Total bytes written: $bytesDownloaded" }
                    outputStream.flush()
                    logcat(LogPriority.VERBOSE, TAG) { "OutputStream flushed." }
                } catch (e: Exception) {
                    logcat(LogPriority.ERROR, TAG) { "Exception during file I/O stream operations: ${e.message}\n${Log.getStackTraceString(e)}" }
                    throw e
                } finally {
                    try {
                        inputStream?.close()
                        logcat(LogPriority.VERBOSE, TAG) { "InputStream closed." }
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, TAG) { "Exception closing InputStream: ${e.message}\n${Log.getStackTraceString(e)}" }
                    }
                    try {
                        outputStream?.close()
                        logcat(LogPriority.VERBOSE, TAG) { "OutputStream closed." }
                    } catch (e: Exception) {
                        logcat(LogPriority.WARN, TAG) { "Exception closing OutputStream: ${e.message}\n${Log.getStackTraceString(e)}" }
                    }
                }
            }
            logcat(TAG) { "File download successful: ${outputFile.absolutePath}" }
            NetworkResult.Success(outputFile)
        } catch (e: Exception) {
            logcat(LogPriority.ERROR, TAG) { "Download failed for URL $downloadUrl: ${e.message}\n${Log.getStackTraceString(e)}" }
            NetworkResult.Error("Download failed: ${e.message}", e)
        }
    }
}
