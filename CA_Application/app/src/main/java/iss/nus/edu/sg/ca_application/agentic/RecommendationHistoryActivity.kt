package iss.nus.edu.sg.ca_application.agentic

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.model.AgentHistoryItem
import android.widget.Toast
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.network.AgentApi

/**
 * History screen — shows past agent-generated recommendations.
 *
 * Lists items newest-first. Each item is a card with date, preview text,
 * and a tap-to-expand evidence trace.
 *
 * Author: Cai Peilin
 */
class RecommendationHistoryActivity : AppCompatActivity() {

    private lateinit var toolbar: MaterialToolbar
    private lateinit var recyclerView: RecyclerView
    private lateinit var emptyText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_recommendation_history)

        toolbar = findViewById(R.id.historyToolbar)
        recyclerView = findViewById(R.id.historyRecycler)
        emptyText = findViewById(R.id.historyEmptyText)

        toolbar.setNavigationOnClickListener { finish() }

        loadHistory()
    }

    private fun loadHistory() {
        Thread {
            try {
                val token = TokenManager.getToken(this@RecommendationHistoryActivity)
                if (token.isEmpty()) {
                    runOnUiThread { Toast.makeText(this@RecommendationHistoryActivity, getString(R.string.agent_error_unauthorized), Toast.LENGTH_LONG).show() }
                    runOnUiThread { renderHistory(emptyList()) }
                    return@Thread
                }
                val items = AgentApi.history(token, limit = 20)
                runOnUiThread { renderHistory(items) }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, e.message, Toast.LENGTH_LONG).show()
                    renderHistory(emptyList())
                }
            }
        }.start()
    }

    private fun renderHistory(items: List<AgentHistoryItem>) {
        if (items.isEmpty()) {
            recyclerView.visibility = View.GONE
            emptyText.visibility = View.VISIBLE
        } else {
            emptyText.visibility = View.GONE
            recyclerView.visibility = View.VISIBLE
            recyclerView.layoutManager = LinearLayoutManager(this)
            recyclerView.adapter = HistoryAdapter(items)
        }
    }
}