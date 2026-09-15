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
    private const val ENDPOINT = "https://openrouter.ai/api/v1/models"

    fun fetchFreeModels(): List<ModelItem> {
        val connection = URL(ENDPOINT).openConnection() as HttpURLConnection
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
                val subtitle = if (context > 0) "context: $context — مجاني بالكامل" else "مجاني بالكامل"
                result.add(ModelItem(title = id, subtitle = subtitle))
            }
        }
        return result.sortedBy { it.title }
    }
}
