package com.tomasthrawat.freemodelsfinder

data class ChatMessage(
    val role: String, // "user", "assistant", or "tool"
    val content: String,
    val attachments: List<String> = emptyList() // display-only file names attached to this message
)
