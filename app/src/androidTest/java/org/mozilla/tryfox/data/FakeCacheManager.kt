package org.mozilla.tryfox.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.mozilla.tryfox.data.managers.CacheManager
import org.mozilla.tryfox.model.CacheManagementState
import java.io.File

class FakeCacheManager : CacheManager {
    private val _cacheState = MutableStateFlow<CacheManagementState>(CacheManagementState.IdleEmpty)
    override val cacheState: StateFlow<CacheManagementState> = _cacheState
    private val _cacheSizeBytes = MutableStateFlow(0L)
    override val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes

    override suspend fun clearCache() {
        _cacheState.value = CacheManagementState.IdleEmpty
        _cacheSizeBytes.value = 0L
    }

    override suspend fun checkCacheStatus() {
        // No-op for now
    }

    override fun getCacheDir(appName: String): File {
        val tempDir = System.getProperty("java.io.tmpdir")
        val dir = File(tempDir, appName)
        if (!dir.exists()) {
            dir.mkdir()
        }
        return dir
    }
}
