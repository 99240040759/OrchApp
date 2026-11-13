package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("orch_preferences")

class OrchPreferences(context: Context) {
    private val store = context.applicationContext.dataStore

    val modelId: Flow<String> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[MODEL].orEmpty() }

    val thinkingEffort: Flow<ThinkingEffort> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { ThinkingEffort.fromWire(it[THINKING_EFFORT]) }

    val activeConnectors: Flow<Set<String>> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map { it[ACTIVE_CONNECTORS] ?: emptySet() }

    suspend fun setModelId(id: String) {
        store.edit { it[MODEL] = id }
    }

    suspend fun setThinkingEffort(effort: ThinkingEffort) {
        store.edit { it[THINKING_EFFORT] = effort.wire }
    }

    suspend fun setActiveConnectors(connectors: Set<String>) {
        store.edit { it[ACTIVE_CONNECTORS] = connectors }
    }

    private companion object {
        val MODEL = stringPreferencesKey("default_model")
        val THINKING_EFFORT = stringPreferencesKey("thinking_effort")
        val ACTIVE_CONNECTORS = stringSetPreferencesKey("active_connectors")
    }
}
