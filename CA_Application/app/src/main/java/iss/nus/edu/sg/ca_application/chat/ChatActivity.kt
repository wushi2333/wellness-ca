package iss.nus.edu.sg.ca_application.chat

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.network.ApiClient
import iss.nus.edu.sg.ca_application.network.ApiException

/**
 * Author: Wang Songyu
 * Chat screen where user can interact with the AI Wellness Assistant.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: ChatAdapter
    private val messages = mutableListOf<ChatMessage>()
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: ImageButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        setupUI()
        addInitialMessage()
    }

    private fun setupUI() {
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        recyclerView = findViewById(R.id.recyclerViewMessages)
        adapter = ChatAdapter(messages)
        recyclerView.adapter = adapter
        recyclerView.layoutManager = LinearLayoutManager(this)

        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)

        buttonSend.setOnClickListener {
            val text = editTextMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
            }
        }
    }

    private fun addInitialMessage() {
        messages.add(ChatMessage("Hello! I'm your Wellness AI Assistant. How can I help you today?", false))
        adapter.notifyItemInserted(messages.size - 1)
    }

    private fun sendMessage(text: String) {
        // Add user message to UI
        val userMessage = ChatMessage(text, true)
        messages.add(userMessage)
        adapter.notifyItemInserted(messages.size - 1)
        recyclerView.smoothScrollToPosition(messages.size - 1)

        editTextMessage.text.clear()

        // Call API
        val token = TokenManager.getToken(this)
        if (token.isEmpty()) {
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        Thread {
            try {
                val reply = ApiClient.sendChatMessage(token, text)
                runOnUiThread {
                    val aiMessage = ChatMessage(reply, false)
                    messages.add(aiMessage)
                    adapter.notifyItemInserted(messages.size - 1)
                    recyclerView.smoothScrollToPosition(messages.size - 1)
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Failed to connect to assistant.", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }
}
