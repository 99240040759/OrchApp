package com.example.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.orchDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "orch_preferences"
)

class OrchCloudPreferences(context: Context) {
    private val dataStore = context.applicationContext.orchDataStore

    val serverUrlFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_SERVER_URL]?.takeIf { it.isNotBlank() } ?: BuildConfig.ORCH_API_BASE_URL
    }

    val defaultModelFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_MODEL].orEmpty()
    }

    val defaultRepoUrlFlow: Flow<String> = dataStore.data.map { prefs ->
        prefs[KEY_DEFAULT_REPO].orEmpty()
    }

    val fcmTokenFlow: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_FCM_TOKEN]
    }

    suspend fun getServerUrl(): String = serverUrlFlow.first()

    suspend fun setServerUrl(url: String) {
        val normalized = url.trim().trimEnd('/').ifEmpty { BuildConfig.ORCH_API_BASE_URL }
        dataStore.edit { prefs ->
            prefs[KEY_SERVER_URL] = normalized
        }
    }

    suspend fun setDefaultModel(modelId: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_MODEL] = modelId.trim()
        }
    }

    suspend fun setDefaultRepoUrl(repoUrl: String) {
        dataStore.edit { prefs ->
            prefs[KEY_DEFAULT_REPO] = repoUrl.trim()
        }
    }

    suspend fun setFcmToken(token: String) {
        dataStore.edit { prefs ->
            prefs[KEY_FCM_TOKEN] = token
        }
    }

    suspend fun getLastEventId(sessionId: String): ULong {
        val key = longPreferencesKey("last_sse_id_$sessionId")
        val raw = dataStore.data.first()[key] ?: 0L
        return raw.coerceAtLeast(0L).toULong()
    }

    suspend fun setLastEventId(sessionId: String, eventId: ULong) {
        val key = longPreferencesKey("last_sse_id_$sessionId")
        dataStore.edit { prefs ->
            prefs[key] = eventId.toLong()
        }
    }

    companion object {
        private val KEY_SERVER_URL = stringPreferencesKey("server_url")
        private val KEY_DEFAULT_MODEL = stringPreferencesKey("default_model")
        private val KEY_DEFAULT_REPO = stringPreferencesKey("default_repo")
        private val KEY_FCM_TOKEN = stringPreferencesKey("fcm_token")
    }
}
