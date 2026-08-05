package org.mozilla.tryfox.data.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mozilla.tryfox.data.InstalledTryBuild

interface InstalledTryBuildRepository {
    val installedTryBuild: Flow<InstalledTryBuild?>

    suspend fun save(build: InstalledTryBuild)
}

object EmptyInstalledTryBuildRepository : InstalledTryBuildRepository {
    override val installedTryBuild: Flow<InstalledTryBuild?> = flowOf(null)

    override suspend fun save(build: InstalledTryBuild) = Unit
}

class DefaultInstalledTryBuildRepository(private val appContext: Context) : InstalledTryBuildRepository {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "installed_try_build")

    override val installedTryBuild: Flow<InstalledTryBuild?> = appContext.dataStore.data.map { preferences ->
        preferences[INSTALLED_TRY_BUILD]?.let { encoded ->
            runCatching { json.decodeFromString<InstalledTryBuild>(encoded) }.getOrNull()
        }
    }

    override suspend fun save(build: InstalledTryBuild) {
        appContext.dataStore.edit { preferences ->
            preferences[INSTALLED_TRY_BUILD] = json.encodeToString(build)
        }
    }

    private companion object {
        val INSTALLED_TRY_BUILD = stringPreferencesKey("installed_try_build")
        val json = Json { ignoreUnknownKeys = true }
    }
}
