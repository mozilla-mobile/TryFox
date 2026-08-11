package org.mozilla.tryfox.download

import android.content.Context
import android.util.Log
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.tryfox.data.managers.NotificationManager
import org.mozilla.tryfox.download.model.DownloadStatus
import org.mozilla.tryfox.download.model.PersistedDownloadState
import org.mozilla.tryfox.download.worker.ApkDownloadWorker

class DefaultApkDownloadCoordinator(
    context: Context,
    private val store: ApkDownloadStore = DefaultApkDownloadStore(context.applicationContext),
    private val workManager: WorkManager = WorkManager.getInstance(context.applicationContext),
    private val notificationManager: NotificationManager,
) : ApkDownloadCoordinator {
    private companion object {
        const val TAG = "ApkDownloadCoordinator"
    }

    override val downloads: StateFlow<Map<String, PersistedDownloadState>> = store.downloads

    override fun enqueue(request: ApkDownloadRequest): String {
        val workRequest =
            OneTimeWorkRequestBuilder<ApkDownloadWorker>()
                .setInputData(ApkDownloadWorker.createInputData(request))
                .addTag(request.uniqueKey)
                .apply {
                    if (notificationManager.areNotificationsEnabled()) {
                        setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    }
                }
                .build()

        store.upsert(
            request.toPersistedState(
                status = DownloadStatus.QUEUED,
                workId = workRequest.id.toString(),
            ),
        )
        workManager.enqueueUniqueWork(request.uniqueKey, ExistingWorkPolicy.REPLACE, workRequest)
        Log.d(
            TAG,
            "enqueued uniqueKey=${request.uniqueKey} workId=${workRequest.id} " +
                "outputPath=${request.outputPath}",
        )
        return workRequest.id.toString()
    }

    override fun retry(request: ApkDownloadRequest): String {
        return enqueue(request)
    }

    override fun cancel(uniqueKey: String) {
        workManager.cancelUniqueWork(uniqueKey)
        store.get(uniqueKey)?.let { current ->
            store.upsert(
                current.copy(
                    status = DownloadStatus.CANCELED,
                    updatedAt = System.currentTimeMillis(),
                ),
            )
        }
    }

    override fun observe(uniqueKey: String): Flow<PersistedDownloadState?> = store.observe(uniqueKey)

    private fun ApkDownloadRequest.toPersistedState(
        status: DownloadStatus,
        workId: String? = null,
        bytesDownloaded: Long = 0L,
        totalBytes: Long = -1L,
        errorMessage: String? = null,
    ): PersistedDownloadState =
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
            workId = workId,
        )
}
