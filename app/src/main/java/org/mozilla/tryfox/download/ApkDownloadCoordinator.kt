package org.mozilla.tryfox.download

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.tryfox.download.model.PersistedDownloadState

interface ApkDownloadCoordinator {
    val downloads: StateFlow<Map<String, PersistedDownloadState>>

    fun enqueue(request: ApkDownloadRequest): String
    fun retry(request: ApkDownloadRequest): String
    fun cancel(uniqueKey: String)
    fun observe(uniqueKey: String): Flow<PersistedDownloadState?>
}
