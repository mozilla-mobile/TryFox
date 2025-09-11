package org.mozilla.fenixinstaller.data.managers

import kotlinx.coroutines.flow.StateFlow
import org.mozilla.fenixinstaller.model.CacheManagementState
import java.io.File

interface CacheManager {
    val cacheState: StateFlow<CacheManagementState>
    suspend fun clearCache()
    fun checkCacheStatus()
    fun getCacheDir(appName: String): File
}
