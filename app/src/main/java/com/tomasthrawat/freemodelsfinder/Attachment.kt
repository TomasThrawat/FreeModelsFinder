package com.tomasthrawat.freemodelsfinder

/**
 * One file the user picked to send alongside (or instead of) a text message.
 * No size limit is enforced when reading it — files of any size and any MIME type are
 * accepted here; OpenRouter or the underlying model is what may ultimately reject
 * something too large.
 */
data class Attachment(
    val filename: String,
    val mimeType: String,
    val base64Data: String
)
