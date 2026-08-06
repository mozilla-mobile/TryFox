package org.mozilla.tryfox.data.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.mozilla.tryfox.data.SearchHistory
import org.mozilla.tryfox.data.SearchHistoryEntry
import org.mozilla.tryfox.data.SearchHistoryQueryType
import org.mozilla.tryfox.lan.LanReceiveIdentity
import org.mozilla.tryfox.model.HomeScreenLayout

/**
 * A repository that stores the last searched email in a DataStore.
 * @param appContext The application context.
 */
class DefaultUserDataRepository(private val appContext: Context) : UserDataRepository {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

    private object PreferenceKeys {
        val USER_EMAIL = stringPreferencesKey("user_email_preference_key")
        val SEARCH_HISTORY = stringPreferencesKey("search_history_preference_key")
        val LAN_DEVICE_ID = stringPreferencesKey("lan_device_id")
        val LAN_DEVICE_NAME = stringPreferencesKey("lan_device_name")
        val LAN_SHARED_SECRET = stringPreferencesKey("lan_shared_secret")
        val HOME_SCREEN_LAYOUT = stringPreferencesKey("home_screen_layout")
    }

    override val searchHistoryFlow: Flow<List<SearchHistoryEntry>> = appContext.dataStore.data.map { preferences ->
        val storedHistory = preferences[PreferenceKeys.SEARCH_HISTORY]
            ?.let(::decodeSearchHistory)
            .orEmpty()
        if (storedHistory.isNotEmpty()) {
            storedHistory
        } else {
            listOfNotNull(SearchHistory.legacyEmailEntry(preferences[PreferenceKeys.USER_EMAIL].orEmpty()))
        }
    }

    override val lastSearchedEmailFlow: Flow<String> = searchHistoryFlow.map(SearchHistory::latestEmail)

    override val lanReceiveIdentityFlow: Flow<LanReceiveIdentity?> = appContext.dataStore.data
        .map { preferences ->
            val deviceId = preferences[PreferenceKeys.LAN_DEVICE_ID]
            val deviceName = preferences[PreferenceKeys.LAN_DEVICE_NAME]
            val sharedSecret = preferences[PreferenceKeys.LAN_SHARED_SECRET]
            if (deviceId.isNullOrBlank() || deviceName.isNullOrBlank() || sharedSecret.isNullOrBlank()) {
                null
            } else {
                LanReceiveIdentity(
                    deviceId = deviceId,
                    deviceName = deviceName,
                    sharedSecret = sharedSecret,
                )
            }
        }

    override val homeScreenLayoutFlow: Flow<HomeScreenLayout> = appContext.dataStore.data.map { preferences ->
        homeScreenLayoutFromStoredValue(preferences[PreferenceKeys.HOME_SCREEN_LAYOUT])
    }

    override suspend fun saveLastSearchedEmail(email: String) {
        recordSearch(project = "try", query = email)
    }

    override suspend fun recordSearch(project: String, query: String, searchedAt: Long) {
        val normalizedQuery = query.trim()
        val queryType = if ('@' in normalizedQuery) SearchHistoryQueryType.EMAIL else SearchHistoryQueryType.REVISION
        val entry = SearchHistoryEntry(project.trim(), normalizedQuery, queryType, searchedAt)
        appContext.dataStore.edit { preferences ->
            val existingEntries = preferences[PreferenceKeys.SEARCH_HISTORY]
                ?.let(::decodeSearchHistory)
                .orEmpty()
                .ifEmpty {
                    listOfNotNull(SearchHistory.legacyEmailEntry(preferences[PreferenceKeys.USER_EMAIL].orEmpty()))
                }
            preferences[PreferenceKeys.SEARCH_HISTORY] = json.encodeToString(SearchHistory.record(existingEntries, entry))
            if (queryType == SearchHistoryQueryType.EMAIL) {
                preferences[PreferenceKeys.USER_EMAIL] = normalizedQuery
            }
        }
    }

    override suspend fun saveLanReceiveIdentity(identity: LanReceiveIdentity) {
        appContext.dataStore.edit { preferences ->
            preferences[PreferenceKeys.LAN_DEVICE_ID] = identity.deviceId
            preferences[PreferenceKeys.LAN_DEVICE_NAME] = identity.deviceName
            preferences[PreferenceKeys.LAN_SHARED_SECRET] = identity.sharedSecret
        }
    }

    override suspend fun saveHomeScreenLayout(layout: HomeScreenLayout) {
        appContext.dataStore.edit { preferences ->
            preferences[PreferenceKeys.HOME_SCREEN_LAYOUT] = layout.name
        }
    }

    private fun decodeSearchHistory(serializedHistory: String): List<SearchHistoryEntry> =
        runCatching { json.decodeFromString<List<SearchHistoryEntry>>(serializedHistory) }.getOrDefault(emptyList())

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
    }
}

internal fun homeScreenLayoutFromStoredValue(storedValue: String?): HomeScreenLayout =
    storedValue
        ?.let { value -> HomeScreenLayout.entries.firstOrNull { it.name == value } }
        ?: HomeScreenLayout.OneCardPerApp
