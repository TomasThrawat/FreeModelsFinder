package com.tomasthrawat.freemodelsfinder

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

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var openRouterList: RecyclerView
    private lateinit var huggingFaceList: RecyclerView

    private val openRouterAdapter = ModelAdapter(emptyList())
    private val huggingFaceAdapter = ModelAdapter(emptyList())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        swipeRefresh = findViewById(R.id.swipeRefresh)
        openRouterList = findViewById(R.id.recyclerOpenRouter)
        huggingFaceList = findViewById(R.id.recyclerHuggingFace)

        openRouterList.layoutManager = LinearLayoutManager(this)
        openRouterList.adapter = openRouterAdapter
        openRouterList.isNestedScrollingEnabled = false

        huggingFaceList.layoutManager = LinearLayoutManager(this)
        huggingFaceList.adapter = huggingFaceAdapter
        huggingFaceList.isNestedScrollingEnabled = false

        swipeRefresh.setOnRefreshListener { loadAll() }

        loadAll()
    }

    private fun loadAll() {
        swipeRefresh.isRefreshing = true
        executor.execute {
            var openRouterError: String? = null
            var hfError: String? = null

            val openRouterModels = try {
                OpenRouterClient.fetchFreeModels()
            } catch (e: Exception) {
                openRouterError = e.message ?: "unknown error"
                emptyList()
            }

            val hfModels = try {
                HuggingFaceClient.fetchCandidateModels()
            } catch (e: Exception) {
                hfError = e.message ?: "unknown error"
                emptyList()
            }

            mainHandler.post {
                openRouterAdapter.updateItems(openRouterModels)
                huggingFaceAdapter.updateItems(hfModels)
                swipeRefresh.isRefreshing = false
                openRouterError?.let {
                    Toast.makeText(this, "OpenRouter: $it", Toast.LENGTH_SHORT).show()
                }
                hfError?.let {
                    Toast.makeText(this, "Hugging Face: $it", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        executor.shutdown()
    }
}
