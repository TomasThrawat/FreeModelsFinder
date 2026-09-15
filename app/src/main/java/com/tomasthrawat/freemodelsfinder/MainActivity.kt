package com.tomasthrawat.freemodelsfinder

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var openRouterList: RecyclerView

    private val openRouterAdapter = ModelAdapter(emptyList()) { model -> openChat(model) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        openRouterList = findViewById(R.id.recyclerOpenRouter)

        openRouterList.layoutManager = LinearLayoutManager(this)
        openRouterList.adapter = openRouterAdapter
        openRouterList.isNestedScrollingEnabled = false

        swipeRefresh.setOnRefreshListener { loadModels() }

        loadModels()
    }

    private fun openChat(model: ModelItem) {
        val intent = Intent(this, ChatActivity::class.java)
        intent.putExtra(ChatActivity.EXTRA_MODEL_ID, model.title)
        startActivity(intent)
    }

    private fun loadModels() {
        swipeRefresh.isRefreshing = true
        executor.execute {
            var error: String? = null
            val models = try {
                OpenRouterClient.fetchFreeModels()
            } catch (e: Exception) {
                error = e.message ?: "unknown error"
                emptyList()
            }
            mainHandler.post {
                openRouterAdapter.updateItems(models)
                swipeRefresh.isRefreshing = false
                error?.let {
                    Toast.makeText(this, "OpenRouter: $it", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
