package com.tomasthrawat.freemodelsfinder

data class ChatMessage(
    val role: String, // "user", "assistant", or "tool"
    val content: String
)
