package com.tomasthrawat.freemodelsfinder

import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

/**
 * IMPORTANT: Hugging Face has no per-model "free" flag like OpenRouter.
 * Every free account gets a small monthly Inference Providers credit ($0.10 at time of writing)
 * that is spent on WHATEVER model you call through the router — there is no fixed zero-cost
 * model list to filter on. This client lists popular Hub models as candidates to try with that
 * credit (or with the legacy rate-limited Serverless Inference API); it does NOT guarantee
 * zero cost the way the OpenRouter list does. That caveat is shown to the user in the UI.
 */
object HuggingFaceClient {
    private const val ENDPOINT =
        "https://huggingface.co/api/models?pipeline_tag=text-generation&sort=likes&direction=-1&limit=25"

    fun fetchCandidateModels(): List<ModelItem> {
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

        val data: JSONArray = JSONArray(body)
        val result = mutableListOf<ModelItem>()
        for (i in 0 until data.length()) {
            val model = data.getJSONObject(i)
            val id = model.optString("id", "?")
            val likes = model.optInt("likes", 0)
            result.add(ModelItem(title = id, subtitle = "likes: $likes — بيستهلك من رصيد الـ credit الشهري"))
        }
        return result
    }
}
