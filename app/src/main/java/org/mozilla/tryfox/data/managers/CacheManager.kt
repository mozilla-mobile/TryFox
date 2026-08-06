package org.mozilla.tryfox.data.managers

import kotlinx.coroutines.flow.StateFlow
import org.mozilla.tryfox.model.CacheManagementState
import java.io.File

interface CacheManager {
    val cacheState: StateFlow<CacheManagementState>
    val cacheSizeBytes: StateFlow<Long>
    suspend fun clearCache()
    suspend fun checkCacheStatus()
    fun getCacheDir(appName: String): File
}
