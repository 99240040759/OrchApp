package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("orch_preferences")

class OrchPreferences(context: Context) {
    private val store = context.applicationContext.dataStore

    val modelId: Flow<String> = store.data.map { it[MODEL].orEmpty() }

    suspend fun setModelId(id: String) {
        store.edit { it[MODEL] = id }
    }

    private companion object {
        val MODEL = stringPreferencesKey("default_model")
    }
}
