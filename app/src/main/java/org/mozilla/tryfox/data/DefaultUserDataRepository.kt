package org.mozilla.tryfox.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * A repository that stores the last searched email in a DataStore.
 * @param appContext The application context.
 */
class DefaultUserDataRepository(
    private val appContext: Context,
) : UserDataRepository {
    private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "user_settings")

    private object PreferenceKeys {
        val USER_EMAIL = stringPreferencesKey("user_email_preference_key")
    }

    override val lastSearchedEmailFlow: Flow<String> =
        appContext.dataStore.data
            .map { preferences ->
                preferences[PreferenceKeys.USER_EMAIL] ?: ""
            }

    override suspend fun saveLastSearchedEmail(email: String) {
        appContext.dataStore.edit { preferences ->
            preferences[PreferenceKeys.USER_EMAIL] = email
        }
    }
}
