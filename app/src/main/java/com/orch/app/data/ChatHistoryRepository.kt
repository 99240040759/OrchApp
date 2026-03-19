package com.orch.app.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class ChatHistoryRepository(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("chat_history", Context.MODE_PRIVATE)

    private val json = Json { 
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    fun getAllConversations(): List<ChatConversation> {
        val raw = prefs.getString("conversations", null) ?: return emptyList()
        return try {
            json.decodeFromString<List<ChatConversation>>(raw)
                .sortedByDescending { it.updatedAt }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getConversation(id: String): ChatConversation? {
        return getAllConversations().find { it.id == id }
    }

    fun saveConversation(conversation: ChatConversation) {
        val all = getAllConversations().toMutableList()
        val index = all.indexOfFirst { it.id == conversation.id }
        val updated = conversation.copy(updatedAt = System.currentTimeMillis())
        if (index >= 0) {
            all[index] = updated
        } else {
            all.add(0, updated)
        }
        prefs.edit().putString("conversations", json.encodeToString(all)).apply()
    }

    fun deleteConversation(id: String) {
        val all = getAllConversations().toMutableList()
        all.removeAll { it.id == id }
        prefs.edit().putString("conversations", json.encodeToString(all)).apply()
    }

    fun deleteAllConversations() {
        prefs.edit().remove("conversations").apply()
    }

    fun renameConversation(id: String, newTitle: String) {
        val all = getAllConversations().toMutableList()
        val index = all.indexOfFirst { it.id == id }
        if (index >= 0) {
            all[index] = all[index].copy(title = newTitle, updatedAt = System.currentTimeMillis())
            prefs.edit().putString("conversations", json.encodeToString(all)).apply()
        }
    }
}
