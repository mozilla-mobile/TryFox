package org.mozilla.tryfox.data.repositories

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.mozilla.tryfox.lan.LanReceiveIdentity

/**
 * A repository that stores the last searched email in a DataStore.
 * @param appContext The application context.
 */
class DefaultUserDataRepository(private val appContext: Context) : UserDataRepository {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

    private object PreferenceKeys {
        val USER_EMAIL = stringPreferencesKey("user_email_preference_key")
        val LAN_DEVICE_ID = stringPreferencesKey("lan_device_id")
        val LAN_DEVICE_NAME = stringPreferencesKey("lan_device_name")
        val LAN_SHARED_SECRET = stringPreferencesKey("lan_shared_secret")
    }

    override val lastSearchedEmailFlow: Flow<String> = appContext.dataStore.data
        .map { preferences ->
            preferences[PreferenceKeys.USER_EMAIL] ?: ""
        }

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

    override suspend fun saveLastSearchedEmail(email: String) {
        appContext.dataStore.edit { preferences ->
            preferences[PreferenceKeys.USER_EMAIL] = email
        }
    }

    override suspend fun saveLanReceiveIdentity(identity: LanReceiveIdentity) {
        appContext.dataStore.edit { preferences ->
            preferences[PreferenceKeys.LAN_DEVICE_ID] = identity.deviceId
            preferences[PreferenceKeys.LAN_DEVICE_NAME] = identity.deviceName
            preferences[PreferenceKeys.LAN_SHARED_SECRET] = identity.sharedSecret
        }
    }
}
