package com.tomasthrawat.freemodelsfinder

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * OpenRouter exposes real per-model pricing at GET /api/v1/models.
 * A model is genuinely free right now when pricing.prompt == 0 AND pricing.completion == 0
 * (these are typically the ":free" suffixed model ids). This list changes without notice,
 * so it is always fetched live — never hardcoded.
 */
object OpenRouterClient {
    private const val MODELS_ENDPOINT = "https://openrouter.ai/api/v1/models"
    private const val CHAT_ENDPOINT = "https://openrouter.ai/api/v1/chat/completions"

    fun fetchFreeModels(): List<ModelItem> {
        val connection = URL(MODELS_ENDPOINT).openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("Accept", "application/json")

        val responseCode = connection.responseCode
        if (responseCode != HttpURLConnection.HTTP_OK) {
            connection.disconnect()
            throw RuntimeException("HTTP $responseCode")
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        connection.disconnect()

        val root = JSONObject(body)
        val data: JSONArray = root.getJSONArray("data")

        val result = mutableListOf<ModelItem>()
        for (i in 0 until data.length()) {
            val model = data.getJSONObject(i)
            val pricing = model.optJSONObject("pricing") ?: continue
            val prompt = pricing.optString("prompt", "-1").toDoubleOrNull() ?: continue
            val completion = pricing.optString("completion", "-1").toDoubleOrNull() ?: continue
            if (prompt == 0.0 && completion == 0.0) {
                val id = model.optString("id", "?")
                val context = model.optInt("context_length", 0)
                val subtitle = if (context > 0) "context: $context — مجانى بالكامل" else "مجانى بالكامل"
                result.add(ModelItem(title = id, subtitle = subtitle))
            }
        }
        return result.sortedBy { it.title }
    }

    /**
     * Sends one OpenAI-compatible chat-completion request to OpenRouter. [tools], when
     * non-null and non-empty, is passed through as-is (OpenAI "function" tool schema) so
     * the model can call MCP-backed tools mid-conversation.
     */
    fun sendChatCompletion(
        apiKey: String,
        model: String,
        messages: JSONArray,
        tools: JSONArray?
    ): JSONObject {
        val connection = URL(CHAT_ENDPOINT).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")

        val payload = JSONObject().apply {
            put("model", model)
            put("messages", messages)
            if (tools != null && tools.length() > 0) {
                put("tools", tools)
            }
        }
        connection.outputStream.use { it.write(payload.toString().toByteArray()) }

        val responseCode = connection.responseCode
        val stream = if (responseCode == HttpURLConnection.HTTP_OK) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
        connection.disconnect()

        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw RuntimeException("HTTP $responseCode: $body")
        }
        return JSONObject(body)
    }
}
