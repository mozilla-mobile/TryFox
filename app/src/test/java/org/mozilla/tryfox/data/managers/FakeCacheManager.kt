package org.mozilla.tryfox.data.managers

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.mozilla.tryfox.model.CacheManagementState
import java.io.File

class FakeCacheManager(private val cacheDir: File) : CacheManager {

    private val _cacheState = MutableStateFlow<CacheManagementState>(CacheManagementState.IdleEmpty)
    override val cacheState: StateFlow<CacheManagementState> = _cacheState.asStateFlow()
    private val _cacheSizeBytes = MutableStateFlow(0L)
    override val cacheSizeBytes: StateFlow<Long> = _cacheSizeBytes.asStateFlow()

    var clearCacheCalled = false
        private set
    var checkCacheStatusCalled = false
        private set
    var getCacheDirCalledWith: String? = null
        private set

    var appCachePopulatedResult: Boolean = false

    override suspend fun clearCache() {
        clearCacheCalled = true
        cacheDir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                child.deleteRecursively()
            }
        }
        // Simulate the behavior of DefaultCacheManager: set to Clearing then to IdleEmpty
        _cacheState.value = CacheManagementState.Clearing
        _cacheSizeBytes.value = 0L
        _cacheState.value = CacheManagementState.IdleEmpty
    }

    override suspend fun checkCacheStatus() {
        checkCacheStatusCalled = true
        // Allow tests to manually set the state or simulate a specific outcome
    }

    override fun getCacheDir(appName: String): File {
        getCacheDirCalledWith = appName
        return File(cacheDir, appName)
    }

    // --- Test specific helpers ---
    fun setCacheState(state: CacheManagementState) {
        _cacheState.value = state
    }

    fun setCacheSizeBytes(sizeBytes: Long) {
        _cacheSizeBytes.value = sizeBytes
        _cacheState.value = if (sizeBytes > 0) CacheManagementState.IdleNonEmpty else CacheManagementState.IdleEmpty
    }

    fun reset() {
        clearCacheCalled = false
        checkCacheStatusCalled = false
        getCacheDirCalledWith = null
        appCachePopulatedResult = false
        _cacheState.value = CacheManagementState.IdleEmpty
        _cacheSizeBytes.value = 0L
    }
}
