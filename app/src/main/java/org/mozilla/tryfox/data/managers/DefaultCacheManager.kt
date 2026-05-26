package org.mozilla.tryfox.data.managers

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import logcat.LogPriority
import logcat.logcat
import org.mozilla.tryfox.model.CacheManagementState
import org.mozilla.tryfox.util.FENIX
import org.mozilla.tryfox.util.FOCUS
import org.mozilla.tryfox.util.FOCUS_RELEASE
import org.mozilla.tryfox.util.REFERENCE_BROWSER
import org.mozilla.tryfox.util.TREEHERDER
import org.mozilla.tryfox.util.TRYFOX
import java.io.File

class DefaultCacheManager(
    private val cacheDir: File,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    legacyCacheDir: File? = null,
) : CacheManager {

    private val _cacheState = MutableStateFlow<CacheManagementState>(CacheManagementState.IdleEmpty)
    override val cacheState: StateFlow<CacheManagementState> = _cacheState.asStateFlow()

    init {
        migrateLegacyCache(legacyCacheDir)
    }

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
        val cacheIsNotEmpty = MANAGED_CACHE_NAMES.any { isAppCachePopulated(it) }
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
                        logcat(LogPriority.DEBUG, TAG) {
                            "Deleting cache directory path=${it.absolutePath}"
                        }
                        it.deleteRecursively()
                    }
                }
            }
            logcat(LogPriority.DEBUG, TAG) { "Cache cleared successfully." }
        } catch (e: Exception) {
            logcat(
                LogPriority.ERROR,
                TAG,
            ) { "Error clearing cache: ${e.message}\n${Log.getStackTraceString(e)}" }
        } finally {
            checkCacheStatus() // Update state to IdleEmpty or whatever is actual
        }
    }

    private fun migrateLegacyCache(legacyCacheDir: File?) {
        if (legacyCacheDir == null || legacyCacheDir == cacheDir || !legacyCacheDir.isDirectory) {
            return
        }

        MANAGED_CACHE_NAMES.forEach { cacheName ->
            val legacyEntry = File(legacyCacheDir, cacheName)
            val target = File(cacheDir, cacheName)
            try {
                if (legacyEntry.isDirectory) {
                    migrateDirectory(legacyEntry, target)
                } else if (legacyEntry.isFile && !target.exists()) {
                    target.parentFile?.mkdirs()
                    legacyEntry.renameTo(target)
                }
            } catch (e: Exception) {
                logcat(LogPriority.WARN, TAG) {
                    "Failed to migrate legacy cache path=${legacyEntry.absolutePath}: ${e.message}"
                }
            }
        }
    }

    private fun migrateDirectory(source: File, target: File) {
        if (!target.exists() && source.renameTo(target)) {
            return
        }

        target.mkdirs()
        source.listFiles()?.forEach { child ->
            val childTarget = File(target, child.name)
            if (child.isDirectory) {
                migrateDirectory(child, childTarget)
            } else if (child.isFile && !childTarget.exists()) {
                childTarget.parentFile?.mkdirs()
                child.renameTo(childTarget)
            }
        }
        source.delete()
    }

    companion object {
        private const val TAG = "DefaultCacheManager"
        private val MANAGED_CACHE_NAMES = listOf(FENIX, FOCUS, FOCUS_RELEASE, REFERENCE_BROWSER, TREEHERDER, TRYFOX)
    }
}
