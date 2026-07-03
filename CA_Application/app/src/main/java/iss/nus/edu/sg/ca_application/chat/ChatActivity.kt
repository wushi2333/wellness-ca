package iss.nus.edu.sg.ca_application.chat

import android.app.AlertDialog
import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.appbar.MaterialToolbar
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.network.ApiClient
import iss.nus.edu.sg.ca_application.network.ApiException
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

/**
 * Author: Huang Qianer
 * Chat screen where user can interact with the AI Wellness Assistant.
 * Uses local storage for chat history with Sidebar management and deletion confirmation.
 */
class ChatActivity : AppCompatActivity() {

    private lateinit var drawerLayout: DrawerLayout
    private lateinit var recyclerViewMessages: RecyclerView
    private lateinit var recyclerViewHistory: RecyclerView
    private lateinit var adapterMessages: ChatAdapter
    private lateinit var adapterHistory: HistoryAdapter
    
    private val messages = mutableListOf<ChatMessage>()
    private val sessions = mutableListOf<HistoryAdapter.ChatSession>()
    
    private lateinit var editTextMessage: EditText
    private lateinit var buttonSend: ImageButton
    private lateinit var btnManage: Button
    private lateinit var btnDeleteAll: Button
    
    private var currentSessionId: String = UUID.randomUUID().toString()
    private var isManageMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_chat)

        setupUI()
        loadSessionsFromPrefs()
        
        if (sessions.isNotEmpty()) {
            loadSession(sessions[0].id)
        } else {
            startNewChat()
        }
    }

    private fun setupUI() {
        drawerLayout = findViewById(R.id.drawerLayout)
        val toolbar = findViewById<MaterialToolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        recyclerViewMessages = findViewById(R.id.recyclerViewMessages)
        adapterMessages = ChatAdapter(messages)
        recyclerViewMessages.adapter = adapterMessages
        recyclerViewMessages.layoutManager = LinearLayoutManager(this)

        recyclerViewHistory = findViewById(R.id.recyclerViewHistory)
        adapterHistory = HistoryAdapter(sessions, 
            onItemClick = { session ->
                if (!isManageMode) {
                    loadSession(session.id)
                    drawerLayout.closeDrawer(GravityCompat.START)
                }
            },
            onDeleteClick = { session ->
                confirmDelete(session.id)
            }
        )
        recyclerViewHistory.adapter = adapterHistory
        recyclerViewHistory.layoutManager = LinearLayoutManager(this)

        editTextMessage = findViewById(R.id.editTextMessage)
        buttonSend = findViewById(R.id.buttonSend)

        buttonSend.setOnClickListener {
            val text = editTextMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                prepareAndSendMessage(text)
            }
        }

        findViewById<Button>(R.id.btnNewChat).setOnClickListener {
            startNewChat()
        }

        findViewById<Button>(R.id.btnHistory).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        btnManage = findViewById(R.id.btnManageSessions)
        btnDeleteAll = findViewById(R.id.btnDeleteAll)

        btnManage.setOnClickListener {
            toggleManageMode()
        }

        btnDeleteAll.setOnClickListener {
            confirmDeleteAll()
        }
        
        drawerLayout.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                if (isManageMode) toggleManageMode()
            }
        })
    }

    private fun toggleManageMode() {
        isManageMode = !isManageMode
        adapterHistory.setManageMode(isManageMode)
        btnManage.text = if (isManageMode) "Done" else "Manage"
        btnDeleteAll.visibility = if (isManageMode) View.VISIBLE else View.GONE
    }

    private fun confirmDelete(id: String) {
        AlertDialog.Builder(this)
            .setTitle("Delete Session")
            .setMessage("Are you sure you want to delete this conversation?")
            .setPositiveButton("Delete") { _, _ -> deleteSession(id) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDeleteAll() {
        AlertDialog.Builder(this)
            .setTitle("Delete All History")
            .setMessage("This will permanently erase all your chat history. Continue?")
            .setPositiveButton("Clear All") { _, _ ->
                deleteAllSessions()
                toggleManageMode()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun addInitialMessage() {
        if (messages.isEmpty()) {
            val welcome = ChatMessage("Hello! I'm your Wellness AI Assistant. How can I help you today?", false)
            messages.add(welcome)
            adapterMessages.notifyItemInserted(messages.size - 1)
        }
    }

    private fun startNewChat() {
        currentSessionId = UUID.randomUUID().toString()
        messages.clear()
        adapterMessages.notifyDataSetChanged()
        addInitialMessage()
    }

    private fun deleteSession(id: String) {
        val prefs = getSharedPreferences("chat_sessions", Context.MODE_PRIVATE)
        prefs.edit().remove("session_$id").apply()
        
        val masterList = prefs.getString("master_list", "[]")
        val array = JSONArray(masterList)
        val newArray = JSONArray()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            if (obj.getString("id") != id) {
                newArray.put(obj)
            }
        }
        prefs.edit().putString("master_list", newArray.toString()).apply()
        
        loadSessionsFromPrefs()
        if (id == currentSessionId) startNewChat()
    }

    private fun deleteAllSessions() {
        val prefs = getSharedPreferences("chat_sessions", Context.MODE_PRIVATE)
        val masterList = prefs.getString("master_list", "[]")
        val array = JSONArray(masterList)
        val editor = prefs.edit()
        
        for (i in 0 until array.length()) {
            val id = array.getJSONObject(i).getString("id")
            editor.remove("session_$id")
        }
        editor.remove("master_list").apply()
        
        loadSessionsFromPrefs()
        startNewChat()
        Toast.makeText(this, "All history cleared", Toast.LENGTH_SHORT).show()
    }

    private fun prepareAndSendMessage(text: String) {
        val userMessage = ChatMessage(text, true)
        messages.add(userMessage)
        adapterMessages.notifyItemInserted(messages.size - 1)
        recyclerViewMessages.smoothScrollToPosition(messages.size - 1)
        editTextMessage.text.clear()

        val token = TokenManager.getToken(this)
        
        Thread {
            try {
                val records = ApiClient.getRecords(token)
                val contextBuilder = StringBuilder()
                if (records.isNotEmpty()) {
                    contextBuilder.append("\n\n[System Note: Below are my recent wellness records for your reference]\n")
                    val limitedRecords = if (records.size > 5) records.take(5) else records
                    for (r in limitedRecords) {
                        contextBuilder.append("- Date: ${r.recordDate}, Sleep: ${r.sleepHours}h, Activity: ${r.exerciseActivity} (${r.exerciseDuration}m)\n")
                    }
                }
                
                val enrichedMessage = text + contextBuilder.toString()
                val reply = ApiClient.sendChatMessage(token, enrichedMessage)
                
                runOnUiThread {
                    val aiMessage = ChatMessage(reply, false)
                    messages.add(aiMessage)
                    adapterMessages.notifyItemInserted(messages.size - 1)
                    recyclerViewMessages.smoothScrollToPosition(messages.size - 1)
                    saveCurrentSession()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Connection failed", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun saveCurrentSession() {
        val prefs = getSharedPreferences("chat_sessions", Context.MODE_PRIVATE)
        val sessionData = JSONArray()
        for (msg in messages) {
            val obj = JSONObject()
            obj.put("content", msg.content)
            obj.put("isUser", msg.isUser)
            obj.put("timestamp", msg.timestamp)
            sessionData.put(obj)
        }
        
        prefs.edit().putString("session_$currentSessionId", sessionData.toString()).apply()
        
        val masterList = prefs.getString("master_list", "[]")
        val sessionsArray = JSONArray(masterList)
        
        var foundIndex = -1
        for (i in 0 until sessionsArray.length()) {
            if (sessionsArray.getJSONObject(i).getString("id") == currentSessionId) {
                foundIndex = i
                break
            }
        }
        if (foundIndex != -1) sessionsArray.remove(foundIndex)

        val newEntry = JSONObject()
        newEntry.put("id", currentSessionId)
        val title = if (messages.size > 1) messages[1].content else messages[0].content
        newEntry.put("title", title)
        newEntry.put("timestamp", System.currentTimeMillis())
        
        val newList = JSONArray()
        newList.put(newEntry)
        for (i in 0 until sessionsArray.length()) {
            newList.put(sessionsArray.get(i))
        }
        
        prefs.edit().putString("master_list", newList.toString()).apply()
        loadSessionsFromPrefs()
    }

    private fun loadSessionsFromPrefs() {
        val prefs = getSharedPreferences("chat_sessions", Context.MODE_PRIVATE)
        val masterList = prefs.getString("master_list", "[]")
        val array = JSONArray(masterList)
        sessions.clear()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            sessions.add(HistoryAdapter.ChatSession(
                obj.getString("id"),
                obj.getString("title"),
                obj.getLong("timestamp")
            ))
        }
        adapterHistory.notifyDataSetChanged()
    }

    private fun loadSession(id: String) {
        val prefs = getSharedPreferences("chat_sessions", Context.MODE_PRIVATE)
        val data = prefs.getString("session_$id", null)
        if (data != null) {
            currentSessionId = id
            val array = JSONArray(data)
            messages.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                messages.add(ChatMessage(
                    obj.getString("content"),
                    obj.getBoolean("isUser"),
                    obj.getLong("timestamp")
                ))
            }
            adapterMessages.notifyDataSetChanged()
            recyclerViewMessages.scrollToPosition(messages.size - 1)
        }
    }
}
