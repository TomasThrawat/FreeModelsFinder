package com.tomasthrawat.freemodelsfinder

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.Executors

/**
 * One-model chat screen, opened by tapping a row on the model list.
 * Talks to OpenRouter's chat-completions endpoint for that exact model id, and — once
 * an MCP server is connected from the overflow menu (e.g. a Composio-generated MCP
 * URL) — passes its tools through so the model can call them mid-conversation.
 */
class ChatActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_MODEL_ID = "model_id"
        private const val PREFS = "free_models_finder_prefs"
        private const val KEY_OPENROUTER_API_KEY = "openrouter_api_key"
        private const val KEY_MCP_URL = "mcp_server_url"
        private const val KEY_MCP_AUTH = "mcp_auth_header"
    }

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var modelId: String
    private lateinit var messageList: RecyclerView
    private lateinit var inputText: EditText
    private lateinit var sendButton: Button
    private lateinit var chatAdapter: ChatAdapter

    private val uiMessages = mutableListOf<ChatMessage>()
    private val apiMessages = JSONArray()

    private var mcpClient: McpClient? = null
    private var mcpConnecting = false

    private val prefs by lazy { getSharedPreferences(PREFS, Context.MODE_PRIVATE) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        modelId = intent.getStringExtra(EXTRA_MODEL_ID) ?: run {
            finish()
            return
        }

        val toolbar = findViewById<Toolbar>(R.id.toolbarChat)
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        title = modelId

        messageList = findViewById(R.id.recyclerChat)
        inputText = findViewById(R.id.editMessage)
        sendButton = findViewById(R.id.buttonSend)

        chatAdapter = ChatAdapter(uiMessages)
        messageList.layoutManager = LinearLayoutManager(this)
        messageList.adapter = chatAdapter

        sendButton.setOnClickListener { onSendClicked() }

        apiMessages.put(JSONObject().apply {
            put("role", "system")
            put("content", "You are a helpful assistant running on the OpenRouter model \"$modelId\".")
        })

        val savedMcpUrl = prefs.getString(KEY_MCP_URL, null)
        if (!savedMcpUrl.isNullOrBlank()) {
            connectMcp(savedMcpUrl, prefs.getString(KEY_MCP_AUTH, null))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.chat_menu, menu)
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val client = mcpClient
        menu.findItem(R.id.action_mcp)?.title = if (client != null) {
            getString(R.string.mcp_connected_menu, client.tools.size)
        } else {
            getString(R.string.mcp_connect_menu)
        }
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_api_key -> {
                promptForApiKey()
                true
            }
            R.id.action_mcp -> {
                promptForMcp()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun onSendClicked() {
        val text = inputText.text.toString().trim()
        if (text.isEmpty()) return
        if (getApiKey().isNullOrBlank()) {
            promptForApiKey()
            return
        }

        inputText.setText("")
        addMessageToUi(ChatMessage("user", text))
        apiMessages.put(JSONObject().apply {
            put("role", "user")
            put("content", text)
        })

        setSending(true)
        runConversationTurn()
    }

    /** Runs one or more OpenRouter calls until the model stops requesting tool calls. */
    private fun runConversationTurn() {
        executor.execute {
            try {
                var keepGoing = true
                while (keepGoing) {
                    val apiKey = getApiKey()
                    if (apiKey.isNullOrBlank()) {
                        postToUi { promptForApiKey() }
                        return@execute
                    }

                    val toolsJson = mcpClient?.tools?.takeIf { it.isNotEmpty() }?.let { toolsToJsonArray(it) }
                    val response = OpenRouterClient.sendChatCompletion(apiKey, modelId, apiMessages, toolsJson)
                    val choices = response.optJSONArray("choices")
                    if (choices == null || choices.length() == 0) {
                        throw RuntimeException("لا يوجد رد من الموديل")
                    }
                    val message = choices.getJSONObject(0).getJSONObject("message")
                    apiMessages.put(message)

                    val content = if (message.isNull("content")) "" else message.optString("content", "")
                    if (content.isNotBlank()) {
                        postToUi { addMessageToUi(ChatMessage("assistant", content)) }
                    }

                    val toolCalls = message.optJSONArray("tool_calls")
                    if (toolCalls != null && toolCalls.length() > 0) {
                        for (i in 0 until toolCalls.length()) {
                            val tc = toolCalls.getJSONObject(i)
                            val tcId = tc.optString("id")
                            val fn = tc.getJSONObject("function")
                            val name = fn.optString("name")
                            val argsText = fn.optString("arguments", "{}")
                            val args = try {
                                JSONObject(argsText)
                            } catch (e: Exception) {
                                JSONObject()
                            }

                            val resultText = try {
                                mcpClient?.callTool(name, args) ?: getString(R.string.mcp_not_connected)
                            } catch (e: Exception) {
                                "${getString(R.string.tool_error_prefix)} ${e.message}"
                            }

                            postToUi { addMessageToUi(ChatMessage("tool", "🔧 $name\n$resultText")) }

                            apiMessages.put(JSONObject().apply {
                                put("role", "tool")
                                put("tool_call_id", tcId)
                                put("content", resultText)
                            })
                        }
                        // loop again so the model can see the tool results
                    } else {
                        keepGoing = false
                    }
                }
            } catch (e: Exception) {
                postToUi { addMessageToUi(ChatMessage("assistant", "خطأ: ${e.message ?: "unknown"}")) }
            } finally {
                postToUi { setSending(false) }
            }
        }
    }

    private fun toolsToJsonArray(mcpTools: List<McpClient.McpTool>): JSONArray {
        val array = JSONArray()
        for (t in mcpTools) {
            array.put(JSONObject().apply {
                put("type", "function")
                put("function", JSONObject().apply {
                    put("name", t.name)
                    put("description", t.description)
                    put("parameters", t.inputSchema)
                })
            })
        }
        return array
    }

    private fun addMessageToUi(message: ChatMessage) {
        chatAdapter.addMessage(message)
        messageList.scrollToPosition(uiMessages.size - 1)
    }

    private fun setSending(sending: Boolean) {
        sendButton.isEnabled = !sending
        inputText.isEnabled = !sending
    }

    private fun postToUi(action: () -> Unit) {
        mainHandler.post(action)
    }

    private fun getApiKey(): String? = prefs.getString(KEY_OPENROUTER_API_KEY, null)

    private fun promptForApiKey() {
        val input = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.openrouter_key_hint)
            setText(getApiKey() ?: "")
        }
        AlertDialog.Builder(this)
            .setTitle(R.string.openrouter_key_title)
            .setView(wrapInPadding(input))
            .setPositiveButton(R.string.save) { _, _ ->
                prefs.edit().putString(KEY_OPENROUTER_API_KEY, input.text.toString().trim()).apply()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun promptForMcp() {
        val urlInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            hint = getString(R.string.mcp_url_hint)
            setText(prefs.getString(KEY_MCP_URL, "") ?: "")
        }
        val authInput = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            hint = getString(R.string.mcp_auth_hint)
            setText(prefs.getString(KEY_MCP_AUTH, "") ?: "")
        }
        val padding = (16 * resources.displayMetrics.density).toInt()
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(urlInput)
            addView(authInput)
        }

        val builder = AlertDialog.Builder(this)
            .setTitle(R.string.mcp_dialog_title)
            .setView(column)
            .setPositiveButton(R.string.connect) { _, _ ->
                val url = urlInput.text.toString().trim()
                val authRaw = authInput.text.toString().trim()
                val auth: String? = if (authRaw.isBlank()) null else authRaw
                if (url.isNotBlank()) {
                    prefs.edit()
                        .putString(KEY_MCP_URL, url)
                        .putString(KEY_MCP_AUTH, auth)
                        .apply()
                    connectMcp(url, auth)
                }
            }
            .setNegativeButton(R.string.cancel, null)

        if (mcpClient != null) {
            builder.setNeutralButton(R.string.disconnect) { _, _ ->
                mcpClient = null
                prefs.edit().remove(KEY_MCP_URL).remove(KEY_MCP_AUTH).apply()
                Toast.makeText(this, R.string.mcp_disconnected, Toast.LENGTH_SHORT).show()
                invalidateOptionsMenu()
            }
        }
        builder.show()
    }

    private fun connectMcp(url: String, auth: String?) {
        if (mcpConnecting) return
        mcpConnecting = true
        val client = McpClient(url, auth)
        executor.execute {
            try {
                val tools = client.connectAndListTools()
                mcpClient = client
                postToUi {
                    Toast.makeText(this, getString(R.string.mcp_connected_toast, tools.size), Toast.LENGTH_SHORT).show()
                    invalidateOptionsMenu()
                }
            } catch (e: Exception) {
                postToUi { Toast.makeText(this, "MCP: ${e.message}", Toast.LENGTH_LONG).show() }
            } finally {
                mcpConnecting = false
            }
        }
    }

    private fun wrapInPadding(view: View): View {
        val padding = (16 * resources.displayMetrics.density).toInt()
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(padding, padding, padding, padding)
            addView(view)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
