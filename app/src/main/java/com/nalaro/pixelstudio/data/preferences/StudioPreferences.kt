package com.nalaro.pixelstudio.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "studio_preferences")

class StudioPreferences(context: Context) {

    private val appContext: Context = context.applicationContext

    private object Keys {
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_download")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val CUSTOM_REPO_OVERRIDE = stringPreferencesKey("custom_repo_override")
    }

    private val safePreferencesFlow: Flow<Preferences> = appContext.dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                emit(emptyPreferences())
            }
        }

    val isWifiOnly: Flow<Boolean> = safePreferencesFlow.map { prefs ->
        prefs[Keys.WIFI_ONLY] ?: true // Default: Wi-Fi only
    }

    val lastUpdateCheckTime: Flow<Long> = safePreferencesFlow.map { prefs ->
        prefs[Keys.LAST_UPDATE_CHECK] ?: 0L
    }

    val selectedModelId: Flow<String> = safePreferencesFlow.map { prefs ->
        prefs[Keys.SELECTED_MODEL_ID] ?: "birefnet-general-lite"
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        try {
            appContext.dataStore.edit { prefs ->
                prefs[Keys.WIFI_ONLY] = enabled
            }
        } catch (_: Throwable) {
        }
    }

    suspend fun recordUpdateCheckTime(timestamp: Long = System.currentTimeMillis()) {
        try {
            appContext.dataStore.edit { prefs ->
                prefs[Keys.LAST_UPDATE_CHECK] = timestamp
            }
        } catch (_: Throwable) {
        }
    }

    suspend fun setSelectedModelId(modelId: String) {
        try {
            appContext.dataStore.edit { prefs ->
                prefs[Keys.SELECTED_MODEL_ID] = modelId
            }
        } catch (_: Throwable) {
        }
    }
}
