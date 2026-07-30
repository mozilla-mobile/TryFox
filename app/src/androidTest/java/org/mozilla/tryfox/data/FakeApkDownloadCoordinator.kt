package org.mozilla.tryfox.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import org.mozilla.tryfox.download.ApkDownloadCoordinator
import org.mozilla.tryfox.download.ApkDownloadRequest
import org.mozilla.tryfox.download.model.PersistedDownloadState

class FakeApkDownloadCoordinator : ApkDownloadCoordinator {
    override val downloads = MutableStateFlow<Map<String, PersistedDownloadState>>(emptyMap())

    override fun enqueue(request: ApkDownloadRequest): String = request.uniqueKey
    override fun retry(request: ApkDownloadRequest): String = request.uniqueKey
    override fun cancel(uniqueKey: String) = Unit
    override fun observe(uniqueKey: String): Flow<PersistedDownloadState?> = flowOf(downloads.value[uniqueKey])
}
