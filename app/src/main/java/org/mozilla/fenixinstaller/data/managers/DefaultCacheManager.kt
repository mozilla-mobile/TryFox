package org.mozilla.fenixinstaller.data.managers

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.mozilla.fenixinstaller.model.CacheManagementState
import java.io.File
import logcat.LogPriority
import logcat.logcat
import org.mozilla.fenixinstaller.util.FENIX
import org.mozilla.fenixinstaller.util.FOCUS
import org.mozilla.fenixinstaller.util.REFERENCE_BROWSER
import org.mozilla.fenixinstaller.util.TREEHERDER

class DefaultCacheManager(
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CacheManager {

    private val _cacheState = MutableStateFlow<CacheManagementState>(CacheManagementState.IdleEmpty)
    override val cacheState: StateFlow<CacheManagementState> = _cacheState.asStateFlow()

    override fun getCacheDir(appName: String): File {
        return File(cacheDir, appName)
    }

    private fun isAppCachePopulated(appName: String): Boolean {
        val appSpecificCacheDir = getCacheDir(appName)
        if (!appSpecificCacheDir.exists() || !appSpecificCacheDir.isDirectory) return false
        appSpecificCacheDir.listFiles()?.forEach { item ->
            if (item.isFile) return true
            if (item.isDirectory) {
                if (item.listFiles()?.any { it.isFile } == true) {
                    return true
                }
            }
        }
        return false
    }

    private fun determineCacheState(): CacheManagementState {
        val cacheIsNotEmpty = listOf(FENIX, FOCUS, REFERENCE_BROWSER, TREEHERDER).any { isAppCachePopulated(it) }
        return if (cacheIsNotEmpty) CacheManagementState.IdleNonEmpty else CacheManagementState.IdleEmpty
    }

    override fun checkCacheStatus() {
        val newCacheState = determineCacheState()
        _cacheState.value = newCacheState
        logcat(TAG) { "Cache status checked. Current state: $newCacheState" }
    }

    override suspend fun clearCache() {
        _cacheState.value = CacheManagementState.Clearing
        try {
            withContext(ioDispatcher) {
                cacheDir.listFiles()?.forEach {
                    if (it.isDirectory) {
                        it.deleteRecursively()
                    }
                }
            }
            logcat(TAG) { "Cache cleared successfully." }
        } catch (e: Exception) {
            logcat(
                LogPriority.ERROR,
                TAG
            ) { "Error clearing cache: ${e.message}\n${Log.getStackTraceString(e)}" }
        } finally {
            checkCacheStatus() // Update state to IdleEmpty or whatever is actual
        }
    }

    companion object {
        private const val TAG = "DefaultCacheManager"
    }
}
