package com.tomasthrawat.freemodelsfinder

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * Minimal MCP (Model Context Protocol) client over the Streamable HTTP transport:
 * JSON-RPC 2.0 messages POSTed to one endpoint, tolerating either a plain JSON body
 * or an SSE-framed ("data: {...}") body per response. Works against any spec-compliant
 * remote MCP server, including a Composio-generated MCP server URL.
 *
 * Call connectAndListTools()/callTool() from a background thread — both do blocking I/O.
 */
class McpClient(
    private val serverUrl: String,
    private val authHeader: String? = null // full header value, e.g. "Bearer xxx"; optional
) {
    data class McpTool(
        val name: String,
        val description: String,
        val inputSchema: JSONObject
    )

    private val idCounter = AtomicInteger(1)
    private var sessionId: String? = null
    private var protocolVersion: String = "2025-03-26"

    var tools: List<McpTool> = emptyList()
        private set

    /** Handshake with the server (initialize + tools/list) and cache the tool list. */
    fun connectAndListTools(): List<McpTool> {
        val initResult = rpcCall(
            "initialize",
            JSONObject().apply {
                put("protocolVersion", protocolVersion)
                put("capabilities", JSONObject())
                put("clientInfo", JSONObject().apply {
                    put("name", "FreeModelsFinder")
                    put("version", "1.0")
                })
            }
        ) ?: throw RuntimeException("MCP: فشل الـ initialize")

        protocolVersion = initResult.optString("protocolVersion", protocolVersion)

        // best-effort notification, per spec, after a successful initialize
        rpcNotify("notifications/initialized", JSONObject())

        val listResult = rpcCall("tools/list", JSONObject())
            ?: throw RuntimeException("MCP: فشل جلب الـ tools")

        val toolsArray: JSONArray = listResult.optJSONArray("tools") ?: JSONArray()
        val parsed = mutableListOf<McpTool>()
        for (i in 0 until toolsArray.length()) {
            val t = toolsArray.getJSONObject(i)
            parsed.add(
                McpTool(
                    name = t.optString("name"),
                    description = t.optString("description", ""),
                    inputSchema = t.optJSONObject("inputSchema") ?: JSONObject().apply {
                        put("type", "object")
                        put("properties", JSONObject())
                    }
                )
            )
        }
        tools = parsed
        return parsed
    }

    /** Executes one MCP tool and returns a plain-text rendering of its result. */
    fun callTool(name: String, arguments: JSONObject): String {
        val result = rpcCall(
            "tools/call",
            JSONObject().apply {
                put("name", name)
                put("arguments", arguments)
            }
        ) ?: return "لا يوجد رد من الأداة"

        val contentArray = result.optJSONArray("content")
        val text = if (contentArray != null) {
            val sb = StringBuilder()
            for (i in 0 until contentArray.length()) {
                val block = contentArray.getJSONObject(i)
                sb.append(if (block.optString("type") == "text") block.optString("text") else block.toString())
                sb.append("\n")
            }
            sb.toString().trim()
        } else {
            result.toString()
        }

        return if (result.optBoolean("isError", false)) "خطأ من الأداة: $text" else text
    }

    private fun rpcNotify(method: String, params: JSONObject) {
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("method", method)
            put("params", params)
        }
        try {
            postRaw(payload)
        } catch (_: Exception) {
            // notifications are best-effort; server may reply with no body
        }
    }

    private fun rpcCall(method: String, params: JSONObject): JSONObject? {
        val id = idCounter.getAndIncrement()
        val payload = JSONObject().apply {
            put("jsonrpc", "2.0")
            put("id", id)
            put("method", method)
            put("params", params)
        }
        val bodyText = postRaw(payload) ?: return null
        val jsonText = extractJson(bodyText) ?: return null
        val root = JSONObject(jsonText)
        if (root.has("error")) {
            val err = root.getJSONObject("error")
            throw RuntimeException(err.optString("message", "MCP error"))
        }
        return root.optJSONObject("result")
    }

    /** POSTs one JSON-RPC payload; returns the raw response body, or null if empty. */
    private fun postRaw(payload: JSONObject): String? {
        val connection = URL(serverUrl).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.doOutput = true
        connection.connectTimeout = 20000
        connection.readTimeout = 20000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json, text/event-stream")
        connection.setRequestProperty("MCP-Protocol-Version", protocolVersion)
        authHeader?.let { connection.setRequestProperty("Authorization", it) }
        sessionId?.let { connection.setRequestProperty("Mcp-Session-Id", it) }

        connection.outputStream.use { it.write(payload.toString().toByteArray()) }

        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val body = stream?.bufferedReader()?.use { it.readText() }

        connection.getHeaderField("Mcp-Session-Id")?.let { sessionId = it }

        if (code !in 200..299) {
            connection.disconnect()
            throw RuntimeException("MCP HTTP $code: ${body ?: ""}")
        }
        connection.disconnect()
        return body?.takeIf { it.isNotBlank() }
    }

    /** Pulls the JSON-RPC object out of a body that may be plain JSON or an SSE stream. */
    private fun extractJson(body: String): String? {
        val trimmed = body.trim()
        if (trimmed.startsWith("{")) return trimmed
        var last: String? = null
        for (line in trimmed.lines()) {
            val l = line.trim()
            if (l.startsWith("data:")) {
                last = l.removePrefix("data:").trim()
            }
        }
        return last
    }
}
