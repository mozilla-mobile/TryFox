package org.mozilla.tryfox.data.repositories

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/** Durable snapshot of successful home-screen release responses. */
interface HomeDataCacheRepository {
    suspend fun read(): HomeDataSnapshot?
    suspend fun write(snapshot: HomeDataSnapshot)
}

object EmptyHomeDataCacheRepository : HomeDataCacheRepository {
    override suspend fun read(): HomeDataSnapshot? = null
    override suspend fun write(snapshot: HomeDataSnapshot) = Unit
}

@Serializable
data class HomeDataSnapshot(
    val version: Int,
    val apps: List<CachedHomeApp>,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class CachedHomeApp(
    val appName: String,
    val apks: List<CachedHomeApk>,
    val selectedReleaseVersion: String? = null,
    val availableReleaseVersions: List<String> = emptyList(),
)

@Serializable
data class CachedHomeApk(
    val originalString: String,
    val rawDateString: String?,
    val appName: String,
    val version: String,
    val abiName: String,
    val fullUrl: String,
    val fileName: String,
)

class DefaultHomeDataCacheRepository(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : HomeDataCacheRepository {
    private val cacheFile = File(context.filesDir, "home-data-cache-v1.json")

    override suspend fun read(): HomeDataSnapshot? = withContext(ioDispatcher) {
        runCatching {
            if (!cacheFile.exists()) return@runCatching null
            json.decodeFromString<HomeDataSnapshot>(cacheFile.readText())
                .takeIf { it.version == HomeDataSnapshot.CURRENT_VERSION }
        }.getOrNull()
    }

    override suspend fun write(snapshot: HomeDataSnapshot) = withContext(ioDispatcher) {
        runCatching {
            cacheFile.parentFile?.mkdirs()
            val temporaryFile = File(cacheFile.parentFile, "${cacheFile.name}.tmp")
            temporaryFile.writeText(json.encodeToString(snapshot))
            if (!temporaryFile.renameTo(cacheFile)) {
                temporaryFile.delete()
            }
        }
        Unit
    }
}
