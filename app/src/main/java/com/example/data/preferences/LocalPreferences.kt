package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.localization.AppLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "mlc_local_prefs")

data class LocalAppPrefs(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val languageSelected: Boolean = false,
    val unlockedUid: String = ""
)

class LocalPreferences(private val context: Context) {
    private object Keys {
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val LANGUAGE_SELECTED = booleanPreferencesKey("language_selected")
        val UNLOCKED_UID = stringPreferencesKey("unlocked_uid")
    }

    val prefsFlow: Flow<LocalAppPrefs> = context.dataStore.data.map { prefs ->
        LocalAppPrefs(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE_CODE]),
            languageSelected = prefs[Keys.LANGUAGE_SELECTED] ?: false,
            unlockedUid = prefs[Keys.UNLOCKED_UID] ?: ""
        )
    }

    suspend fun saveLanguage(language: AppLanguage, markSelected: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LANGUAGE_CODE] = language.code
            if (markSelected) {
                prefs[Keys.LANGUAGE_SELECTED] = true
            }
        }
    }

    suspend fun setUnlockedUid(uid: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.UNLOCKED_UID] = uid
        }
    }

    suspend fun clearSessionLock() {
        context.dataStore.edit { prefs ->
            prefs[Keys.UNLOCKED_UID] = ""
        }
    }
}
