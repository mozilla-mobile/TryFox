package org.mozilla.tryfox.download

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mozilla.tryfox.download.model.PersistedDownloadState
import java.io.File

interface ApkDownloadStore {
    val downloads: StateFlow<Map<String, PersistedDownloadState>>

    fun observe(uniqueKey: String): Flow<PersistedDownloadState?>
    fun get(uniqueKey: String): PersistedDownloadState?
    fun upsert(state: PersistedDownloadState)
    fun remove(uniqueKey: String)
    fun clear()
}

class DefaultApkDownloadStore(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    },
) : ApkDownloadStore {
    private val scope = kotlinx.coroutines.CoroutineScope(SupervisorJob() + ioDispatcher)
    private val lock = Mutex()
    private val stateFile = File(context.filesDir, "apk-download-state.json")
    private val _downloads = MutableStateFlow(loadInitialState())
    override val downloads: StateFlow<Map<String, PersistedDownloadState>> = _downloads.asStateFlow()

    override fun observe(uniqueKey: String): Flow<PersistedDownloadState?> = downloads.map { it[uniqueKey] }

    override fun get(uniqueKey: String): PersistedDownloadState? = downloads.value[uniqueKey]

    override fun upsert(state: PersistedDownloadState) {
        _downloads.value = _downloads.value + (state.uniqueKey to state)
        schedulePersist()
    }

    override fun remove(uniqueKey: String) {
        _downloads.value = _downloads.value - uniqueKey
        schedulePersist()
    }

    override fun clear() {
        _downloads.value = emptyMap()
        schedulePersist()
    }

    private fun schedulePersist() {
        scope.launch {
            lock.withLock {
                persistLocked(_downloads.value)
            }
        }
    }

    private fun loadInitialState(): Map<String, PersistedDownloadState> =
        try {
            if (!stateFile.exists()) {
                emptyMap()
            } else {
                runBlocking(ioDispatcher) {
                    lock.withLock {
                        val raw = stateFile.readText()
                        json.decodeFromString<Map<String, PersistedDownloadState>>(raw)
                    }
                }
            }
        } catch (_: SerializationException) {
            emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }

    private fun persistLocked(downloads: Map<String, PersistedDownloadState>) {
        try {
            stateFile.parentFile?.mkdirs()
            stateFile.writeText(json.encodeToString(downloads))
        } catch (_: Exception) {
            // Best effort persistence. The in-memory state remains authoritative until the next write.
        }
    }
}
