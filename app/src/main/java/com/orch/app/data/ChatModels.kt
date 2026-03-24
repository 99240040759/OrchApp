package com.orch.app.data

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val id: String = java.util.UUID.randomUUID().toString(),
    val text: String,
    val isUser: Boolean,
    val thinkingText: String = "",   // <think>…</think> reasoning chain (if any)
    val thinkingDurationMs: Long = 0,
    val isThinking: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class ChatConversation(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String = "New Chat",
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
