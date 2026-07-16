package com.rhuta.kask.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class ChatMessage(
    val role: String, // "user", "assistant", "system"
    val content: String,
    val attachmentUri: String? = null,
    val attachmentType: String? = null, // e.g. "image", "pdf"
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class Conversation(
    val messages: List<ChatMessage> = emptyList()
) {
    fun toSystemPrompt(): String {
        return messages.joinToString("\n") { msg ->
            when (msg.role) {
                "user" -> {
                    val prefix = if (msg.attachmentUri != null) "[Attached ${msg.attachmentType}: ${msg.attachmentUri}]\n" else ""
                    "<|im_start|>user\n$prefix${msg.content}<|im_end|>"
                }
                "assistant" -> "<|im_start|>assistant\n${msg.content}<|im_end|>"
                else -> "<|im_start|>system\n${msg.content}<|im_end|>"
            }
        }
    }
}
