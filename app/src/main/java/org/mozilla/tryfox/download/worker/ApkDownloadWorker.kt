package org.mozilla.tryfox.download.worker

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.mozilla.tryfox.data.NetworkResult
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.data.repositories.DownloadFileRepository
import org.mozilla.tryfox.download.ApkDownloadRequest
import org.mozilla.tryfox.download.ApkDownloadStore
import org.mozilla.tryfox.download.DownloadNotificationFactory
import org.mozilla.tryfox.download.model.DownloadStatus
import org.mozilla.tryfox.download.model.PersistedDownloadState
import java.io.File

class ApkDownloadWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params), KoinComponent {
    private val downloadFileRepository: DownloadFileRepository by inject()
    private val cacheManager: CacheManager by inject()
    private val downloadStore: ApkDownloadStore by inject()
    private val notificationFactory: DownloadNotificationFactory by inject()

    override suspend fun doWork(): Result {
        val request = inputData.toRequest() ?: return Result.failure()
        Log.d(TAG, "started uniqueKey=${request.uniqueKey} workerId=$id outputPath=${request.outputPath}")
        val startedAt = System.currentTimeMillis()
        var lastBytesDownloaded = 0L
        var lastTotalBytes = -1L
        var lastProgressUpdateAt = 0L
        var lastProgressPercent = -1
        setForeground(notificationFactory.createForegroundInfo(request.appName))

        updateState(
            request = request,
            status = DownloadStatus.RUNNING,
            startedAt = startedAt,
            bytesDownloaded = 0L,
            totalBytes = -1L,
        )

        val outputFile = File(request.outputPath)
        outputFile.parentFile?.mkdirs()

        return try {
            when (
                val result = withContext(Dispatchers.IO) {
                    downloadFileRepository.downloadFile(
                        downloadUrl = request.downloadUrl,
                        outputFile = outputFile,
                    ) { bytesDownloaded, totalBytes ->
                        lastBytesDownloaded = bytesDownloaded
                        lastTotalBytes = totalBytes
                        val progressPercent =
                            if (totalBytes > 0) ((bytesDownloaded * 100) / totalBytes).toInt() else -1
                        val now = System.currentTimeMillis()
                        val elapsedSinceLastUpdate = now - lastProgressUpdateAt
                        val shouldPublish =
                            if (totalBytes <= 0) {
                                // Some Taskcluster artifact responses omit Content-Length. The
                                // progress UI is necessarily indeterminate, but publishing on
                                // every 4 KiB read overwhelms the state store and logcat.
                                lastProgressUpdateAt == 0L || elapsedSinceLastUpdate >= PROGRESS_UPDATE_INTERVAL_MS
                            } else {
                                lastProgressPercent < 0 ||
                                    bytesDownloaded == totalBytes ||
                                    progressPercent >= lastProgressPercent + MIN_PROGRESS_PERCENT_STEP ||
                                    elapsedSinceLastUpdate >= PROGRESS_UPDATE_INTERVAL_MS
                            }
                        if (shouldPublish) {
                            lastProgressUpdateAt = now
                            lastProgressPercent = progressPercent
                            updateState(
                                request = request,
                                status = DownloadStatus.RUNNING,
                                startedAt = startedAt,
                                bytesDownloaded = bytesDownloaded,
                                totalBytes = totalBytes,
                            )
                            setProgress(
                                workDataOf(
                                    KEY_UNIQUE_KEY to request.uniqueKey,
                                    KEY_BYTES_DOWNLOADED to bytesDownloaded,
                                    KEY_TOTAL_BYTES to totalBytes,
                                ),
                            )
                            setForeground(
                                notificationFactory.createForegroundInfo(
                                    appName = request.appName,
                                    progress = if (totalBytes > 0) progressPercent else null,
                                    isIndeterminate = totalBytes <= 0,
                                ),
                            )
                        }
                    }
                }
            ) {
                is NetworkResult.Success -> {
                    val downloadedFile = result.data.takeIf { it.exists() } ?: outputFile.takeIf { it.exists() }
                    if (downloadedFile == null) {
                        updateFailure(
                            request = request,
                            message = "Downloaded file is missing",
                            startedAt = startedAt,
                        )
                        Result.failure(
                            workDataOf(
                                KEY_UNIQUE_KEY to request.uniqueKey,
                                KEY_ERROR_MESSAGE to "Downloaded file is missing",
                            ),
                        )
                    } else {
                        Log.d(
                            TAG,
                            "download completed uniqueKey=${request.uniqueKey} file=${downloadedFile.absolutePath} " +
                                "bytes=$lastBytesDownloaded total=$lastTotalBytes",
                        )
                        updateSuccess(
                            request = request,
                            startedAt = startedAt,
                            bytesDownloaded = lastBytesDownloaded,
                            totalBytes = lastTotalBytes,
                        )
                        cacheManager.checkCacheStatus()
                        Result.success(
                            workDataOf(
                                KEY_UNIQUE_KEY to request.uniqueKey,
                                KEY_OUTPUT_PATH to downloadedFile.absolutePath,
                            ),
                        )
                    }
                }

                is NetworkResult.Error -> {
                    Log.e(TAG, "download failed uniqueKey=${request.uniqueKey}: ${result.message}")
                    updateFailure(
                        request = request,
                        message = result.message,
                        startedAt = startedAt,
                    )
                    cacheManager.checkCacheStatus()
                    Result.failure(
                        workDataOf(
                            KEY_UNIQUE_KEY to request.uniqueKey,
                            KEY_ERROR_MESSAGE to result.message,
                        ),
                    )
                }
            }
        } catch (e: CancellationException) {
            Log.w(TAG, "download cancelled uniqueKey=${request.uniqueKey}")
            updateCanceled(request = request, startedAt = startedAt)
            cacheManager.checkCacheStatus()
            throw e
        }
    }

    private fun updateSuccess(
        request: ApkDownloadRequest,
        startedAt: Long,
        bytesDownloaded: Long,
        totalBytes: Long,
    ) {
        if (!isCurrentRequest(request)) return
        downloadStore.upsert(
            request.toPersistedState(
                status = DownloadStatus.SUCCEEDED,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                updatedAt = System.currentTimeMillis(),
                createdAt = startedAt,
            ),
        )
    }

    private fun updateFailure(request: ApkDownloadRequest, message: String?, startedAt: Long) {
        if (!isCurrentRequest(request)) return
        Log.e(TAG, "recording failure uniqueKey=${request.uniqueKey}: $message")
        downloadStore.upsert(
            request.toPersistedState(
                status = DownloadStatus.FAILED,
                errorMessage = message,
                updatedAt = System.currentTimeMillis(),
                createdAt = startedAt,
            ),
        )
    }

    private fun updateCanceled(request: ApkDownloadRequest, startedAt: Long) {
        if (!isCurrentRequest(request)) return
        downloadStore.upsert(
            request.toPersistedState(
                status = DownloadStatus.CANCELED,
                updatedAt = System.currentTimeMillis(),
                createdAt = startedAt,
            ),
        )
    }

    private fun updateState(
        request: ApkDownloadRequest,
        status: DownloadStatus,
        startedAt: Long,
        bytesDownloaded: Long,
        totalBytes: Long,
    ) {
        if (!isCurrentRequest(request)) return
        downloadStore.upsert(
            request.toPersistedState(
                status = status,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                updatedAt = System.currentTimeMillis(),
                createdAt = startedAt,
            ),
        )
    }

    private fun ApkDownloadRequest.toPersistedState(
        status: DownloadStatus,
        bytesDownloaded: Long = 0L,
        totalBytes: Long = -1L,
        errorMessage: String? = null,
        workId: String? = null,
        createdAt: Long = System.currentTimeMillis(),
        updatedAt: Long = createdAt,
    ): PersistedDownloadState =
        downloadStore.get(uniqueKey)?.let { existing ->
            PersistedDownloadState(
                uniqueKey = uniqueKey,
                downloadUrl = downloadUrl,
                outputPath = outputPath,
                appName = appName,
                fileName = fileName,
                cacheRelativePath = cacheRelativePath,
                status = status,
                bytesDownloaded = bytesDownloaded,
                totalBytes = totalBytes,
                errorMessage = errorMessage,
                workId = workId ?: existing.workId,
                createdAt = existing.createdAt,
                updatedAt = updatedAt,
            )
        } ?: PersistedDownloadState(
            uniqueKey = uniqueKey,
            downloadUrl = downloadUrl,
            outputPath = outputPath,
            appName = appName,
            fileName = fileName,
            cacheRelativePath = cacheRelativePath,
            status = status,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
            errorMessage = errorMessage,
            workId = workId,
            createdAt = createdAt,
            updatedAt = updatedAt,
        )

    private fun isCurrentRequest(request: ApkDownloadRequest): Boolean {
        val persistedState = downloadStore.get(request.uniqueKey)
        val isCurrent = persistedState?.workId == id.toString() && persistedState.status != DownloadStatus.CANCELED
        if (!isCurrent) {
            Log.w(
                TAG,
                "ignoring stale worker uniqueKey=${request.uniqueKey} workerId=$id " +
                    "storedWorkId=${persistedState?.workId} storedStatus=${persistedState?.status}",
            )
        }
        return isCurrent
    }

    private fun Data.toRequest(): ApkDownloadRequest? {
        val uniqueKey = getString(KEY_UNIQUE_KEY) ?: return null
        val downloadUrl = getString(KEY_DOWNLOAD_URL) ?: return null
        val outputPath = getString(KEY_OUTPUT_PATH) ?: return null
        val appName = getString(KEY_APP_NAME) ?: return null
        val fileName = getString(KEY_FILE_NAME) ?: return null
        val cacheRelativePath = getString(KEY_CACHE_RELATIVE_PATH)

        return ApkDownloadRequest(
            uniqueKey = uniqueKey,
            downloadUrl = downloadUrl,
            outputFile = File(outputPath),
            appName = appName,
            fileName = fileName,
            cacheRelativePath = cacheRelativePath,
        )
    }

    companion object {
        private const val TAG = "ApkDownloadWorker"
        private const val PROGRESS_UPDATE_INTERVAL_MS = 500L
        private const val MIN_PROGRESS_PERCENT_STEP = 5
        const val KEY_UNIQUE_KEY = "download_unique_key"
        const val KEY_DOWNLOAD_URL = "download_url"
        const val KEY_OUTPUT_PATH = "download_output_path"
        const val KEY_APP_NAME = "download_app_name"
        const val KEY_FILE_NAME = "download_file_name"
        const val KEY_CACHE_RELATIVE_PATH = "download_cache_relative_path"
        const val KEY_BYTES_DOWNLOADED = "download_bytes_downloaded"
        const val KEY_TOTAL_BYTES = "download_total_bytes"
        const val KEY_ERROR_MESSAGE = "download_error_message"

        fun createInputData(request: ApkDownloadRequest): Data =
            Data.Builder()
                .putString(KEY_UNIQUE_KEY, request.uniqueKey)
                .putString(KEY_DOWNLOAD_URL, request.downloadUrl)
                .putString(KEY_OUTPUT_PATH, request.outputPath)
                .putString(KEY_APP_NAME, request.appName)
                .putString(KEY_FILE_NAME, request.fileName)
                .apply {
                    request.cacheRelativePath?.let { putString(KEY_CACHE_RELATIVE_PATH, it) }
                }
                .build()
    }
}
