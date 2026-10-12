package com.nalaro.pixelstudio.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "studio_preferences")

class StudioPreferences(private val context: Context) {

    private object Keys {
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_download")
        val LAST_UPDATE_CHECK = longPreferencesKey("last_update_check")
        val SELECTED_MODEL_ID = stringPreferencesKey("selected_model_id")
        val CUSTOM_REPO_OVERRIDE = stringPreferencesKey("custom_repo_override")
    }

    val isWifiOnly: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.WIFI_ONLY] ?: true // Default: Wi-Fi only
    }

    val lastUpdateCheckTime: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_UPDATE_CHECK] ?: 0L
    }

    val selectedModelId: Flow<String> = context.dataStore.data.map { prefs ->
        prefs[Keys.SELECTED_MODEL_ID] ?: "birefnet-general-lite"
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.dataStore.edit { prefs ->
            prefs[Keys.WIFI_ONLY] = enabled
        }
    }

    suspend fun recordUpdateCheckTime(timestamp: Long = System.currentTimeMillis()) {
        context.dataStore.edit { prefs ->
            prefs[Keys.LAST_UPDATE_CHECK] = timestamp
        }
    }

    suspend fun setSelectedModelId(modelId: String) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SELECTED_MODEL_ID] = modelId
        }
    }
}
