// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.character

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.app.Dialog
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.MainActivity
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.live2d.Live2DCharacterView
import iss.nus.edu.sg.ca_application.model.*
import iss.nus.edu.sg.ca_application.network.ApiException
import iss.nus.edu.sg.ca_application.network.CharacterApi

class CharacterChatActivity : AppCompatActivity() {

    private lateinit var token: String
    private var currentSessionId: Long? = null
    private var hasSentMessage = false // track if user actually used this session
    private var currentMode = "chat"
    private val adapter = CharacterChatAdapter()
    private lateinit var drawer: DrawerLayout
    private lateinit var recycler: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var tvSessionTitle: TextView
    private lateinit var btnMode: Button
    private var voiceEnabled = true
    private lateinit var live2dView: Live2DCharacterView
    private lateinit var sessionRecycler: RecyclerView
    private val sessionAdapter = SessionAdapter()
    private val handler = Handler(Looper.getMainLooper())
    private var titlePollRunnable: Runnable? = null

    // Audio recording for Volcano ASR
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recorderThread: Thread? = null
    private val recordedChunks = mutableListOf<ByteArray>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_character_chat)

        token = TokenManager.getToken(this)
        drawer = findViewById(R.id.drawerLayout)
        recycler = findViewById(R.id.chatRecycler)
        etMessage = findViewById(R.id.etMessage)
        tvSessionTitle = findViewById(R.id.tvSessionTitle)
        btnMode = findViewById(R.id.btnModeToggle)
        live2dView = findViewById(R.id.live2dView)

        // Loading dialog — only on cold start (first time model loads from disk)
        val isColdStart = !live2dView.isModelCached()
        val loadingDialog: Dialog?
        if (isColdStart) {
            loadingDialog = Dialog(this, android.R.style.Theme_Translucent_NoTitleBar)
            loadingDialog.setContentView(R.layout.overlay_loading)
            loadingDialog.findViewById<Button>(R.id.btnExit).setOnClickListener { finish() }
            loadingDialog.setCancelable(false)
            loadingDialog.show()
        } else {
            loadingDialog = null
        }

        iss.nus.edu.sg.ca_application.live2d.LAppMinimumLive2DManager
            .setOnReadyStatic { runOnUiThread { loadingDialog?.dismiss() } }

        recycler.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        recycler.adapter = adapter

        sessionRecycler = findViewById(R.id.sessionRecycler)
        sessionRecycler.layoutManager = LinearLayoutManager(this)
        sessionRecycler.adapter = sessionAdapter

        findViewById<ImageButton>(R.id.btnSend).setOnClickListener { sendMessage() }

        findViewById<Button>(R.id.btnMic).setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startAudioRecording(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) stopAudioRecording(); true
                }
                else -> false
            }
        }

        findViewById<Button>(R.id.btnNewChat).setOnClickListener { createNewSession() }
        findViewById<Button>(R.id.btnSidebarNewChat).setOnClickListener { createNewSession() }
        // Trash icon → toggle selection mode
        findViewById<ImageButton>(R.id.btnTrash).setOnClickListener { sessionAdapter.toggleSelectionMode() }
        // Selection action buttons
        findViewById<Button>(R.id.btnDeleteSelected).setOnClickListener {
            val n = sessionAdapter.selectedCount
            if (n > 0) {
                AlertDialog.Builder(this)
                    .setTitle("Delete Selected")
                    .setMessage("Delete $n selected chat(s)?")
                    .setPositiveButton("Delete") { _, _ -> sessionAdapter.deleteSelected() }
                    .setNegativeButton("Cancel", null).show()
            }
        }
        findViewById<Button>(R.id.btnDeleteAll).setOnClickListener {
            AlertDialog.Builder(this)
                .setTitle("Delete All")
                .setMessage("Delete ALL chats? This cannot be undone.")
                .setPositiveButton("Delete All") { _, _ -> sessionAdapter.deleteAllSessions() }
                .setNegativeButton("Cancel", null).show()
        }
        findViewById<Button>(R.id.btnSessions).setOnClickListener {
            loadSessions(); drawer.openDrawer(Gravity.START)
        }
        drawer.addDrawerListener(object : androidx.drawerlayout.widget.DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(drawerView: View) {
                sessionAdapter.exitSelectionMode()
            }
        })
        btnMode.setOnClickListener { toggleMode() }

        // Test button: cycle through all emotions
        val emotions = arrayOf("happy", "listening", "surprised", "focused", "confused", "thinking")
        var emotionIdx = 0
        findViewById<Button>(R.id.btnTestEmotion).setOnClickListener {
            val emo = emotions[emotionIdx % emotions.size]
            live2dView.setEmotion(emo)
            toast("Emotion: $emo")
            emotionIdx++
        }

        btnMode.text = "Chat"
        btnMode.backgroundTintList = resources.getColorStateList(R.color.primary, null)

        val btnVoice = findViewById<Button>(R.id.btnToggleVoice)
        btnVoice.setOnClickListener {
            voiceEnabled = !voiceEnabled
            btnVoice.text = if (voiceEnabled) "🔊" else "🔇"
        }

        // Show cold-start welcome locally (no session created yet)
        if (firstSessionAfterColdStart) {
            firstSessionAfterColdStart = false
            val username = TokenManager.getUsername(this).ifEmpty { "User" }
            adapter.addAssistantMessage(getString(R.string.yui_welcome, username), null)
        }

        // Don't create session on server yet — defer to sendMessage()
    }

    override fun onResume() {
        super.onResume()
        token = TokenManager.getToken(this)
        live2dView.onStart()
    }

    override fun onPause() {
        super.onPause()
        live2dView.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        titlePollRunnable?.let { handler.removeCallbacks(it) }
        live2dView.onDestroy()
        // Clean up audio recorder if still active
        isRecording = false
        recorderThread?.interrupt()
        recorderThread = null
        audioRecord?.apply {
            try { stop() } catch (_: Exception) {}
            try { release() } catch (_: Exception) {}
        }
        audioRecord = null
    }

    private fun loadSessions() {
        Thread {
            try {
                val sessions = CharacterApi.getSessions(token)
                runOnUiThread { sessionAdapter.setSessions(sessions) }
            } catch (e: Exception) { /* silent */ }
        }.start()
    }

    private fun createNewSession() {
        if (adapter.lastIndex < 0) {
            drawer.closeDrawer(Gravity.START)
            return
        }
        // Only reset local UI — session is created lazily on first sendMessage()
        currentSessionId = null
        hasSentMessage = false
        tvSessionTitle.text = "New Chat"
        adapter.setMessages(emptyList())
        etMessage.hint = "Chat with Yui 🌸"
        drawer.closeDrawer(Gravity.START)
    }

    private fun switchToSession(sessionId: Long, title: String) {
        currentSessionId = sessionId
        hasSentMessage = true // existing session already has content
        tvSessionTitle.text = title
        adapter.setMessages(emptyList())
        etMessage.hint = "Chat with Yui 🌸"
        loadMessages(sessionId)
    }

    private fun loadMessages(sessionId: Long) {
        Thread {
            try {
                val msgs = CharacterApi.getMessages(token, sessionId)
                runOnUiThread {
                    adapter.setMessages(msgs)
                    if (msgs.isNotEmpty()) hasSentMessage = true
                    recycler.scrollToPosition(adapter.itemCount - 1)
                }
            } catch (e: Exception) { /* empty */ }
        }.start()
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return
        hasSentMessage = true
        etMessage.text.clear()
        etMessage.hint = "Type a message…"
        findViewById<ImageButton>(R.id.btnSend).isEnabled = false

        // Show user message immediately
        adapter.addUserMessage(text)
        recycler.scrollToPosition(adapter.lastIndex)

        // Show loading bubble for AI
        adapter.showLoading()
        recycler.scrollToPosition(adapter.lastIndex)

        Thread {
            try {
                // Create session lazily on first message (avoids empty sessions)
                if (currentSessionId == null) {
                    val s = CharacterApi.createSession(token, currentMode)
                    currentSessionId = s.id
                }
                val resp = CharacterApi.chat(token, text, currentMode, currentSessionId)
                runOnUiThread {
                    currentSessionId = resp.sessionId
                    adapter.updateLoadingReply(resp.reply, resp.emotion, resp.tools)
                    live2dView.setEmotion(resp.emotion)
                    recycler.scrollToPosition(adapter.lastIndex)
                    // Play TTS with mouth sync
                    if (voiceEnabled) speakResponse(resp.reply, resp.emotion)
                    findViewById<ImageButton>(R.id.btnSend).isEnabled = true

                    // Poll for updated title (generated async on server)
                    pollSessionTitle(resp.sessionId)

                    resp.intent?.let { handleIntent(it) }
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    if (e.code == 401) {
                        TokenManager.clearToken(this)
                        startActivity(Intent(this, MainActivity::class.java))
                        finish()
                    } else {
                        adapter.updateLoadingReply("Sorry, something went wrong. Try again?", "confused")
                        findViewById<ImageButton>(R.id.btnSend).isEnabled = true
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    adapter.updateLoadingReply("Network error. Please try again.", "confused")
                    findViewById<ImageButton>(R.id.btnSend).isEnabled = true
                }
            }
        }.start()
    }

    private fun pollSessionTitle(sessionId: Long) {
        titlePollRunnable?.let { handler.removeCallbacks(it) }
        val runnable = object : Runnable {
            override fun run() {
                Thread {
                    try {
                        val sessions = CharacterApi.getSessions(token)
                        val s = sessions.find { it.id == sessionId }
                        if (s != null && s.title != "New Chat") {
                            runOnUiThread {
                                if (currentSessionId == sessionId) {
                                    tvSessionTitle.text = s.title
                                }
                                sessionAdapter.setSessions(sessions)
                            }
                            return@Thread
                        }
                        // Poll again after 2 seconds
                        handler.postDelayed(this, 2000)
                    } catch (e: Exception) { /* stop polling */ }
                }.start()
            }
        }
        titlePollRunnable = runnable
        handler.postDelayed(runnable, 2000)
    }

    private fun handleIntent(intent: Map<String, String>) {
        if (intent["action"] != "navigate") return
        when (intent["target"]) {
            "wellness_list" -> startActivity(Intent(this, iss.nus.edu.sg.ca_application.wellness.WellnessListActivity::class.java))
            "wellness_entry" -> startActivity(Intent(this, iss.nus.edu.sg.ca_application.wellness.WellnessEntryActivity::class.java))
            "wellness_insights" -> startActivity(Intent(this, iss.nus.edu.sg.ca_application.agentic.RecommendationActivity::class.java))
            "dashboard" -> startActivity(Intent(this, MainActivity::class.java))
        }
    }

    private fun toggleMode() {
        currentMode = if (currentMode == "chat") "agent" else "chat"
        btnMode.text = if (currentMode == "agent") "Agent" else "Chat"
        btnMode.backgroundTintList = resources.getColorStateList(
            if (currentMode == "agent") R.color.secondary else R.color.primary, null)
        live2dView.setModeExpression(currentMode)
    }

    private fun speakResponse(text: String, emotion: String) {
        val model = iss.nus.edu.sg.ca_application.live2d.LAppMinimumLive2DManager.getInstance().getModel(0)
        Thread {
            val result = VolcanoTtsService.synthesize(text, emotion, TokenManager.getToken(this))
            if (result != null) {
                runOnUiThread {
                    VolcanoTtsService.playMp3(
                        this@CharacterChatActivity, result,
                        onMouth = { v -> model?.mouthOpenOverride = v },
                        onComplete = { model?.mouthOpenOverride = 0f }
                    )
                }
            }
        }.start()
    }

    // ---- Volcano ASR: hold-to-talk audio recording -----------------------

    private fun startAudioRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_RECORD_AUDIO)
            return
        }
        if (isRecording) return

        val sampleRate = 16000
        val bufSize = AudioRecord.getMinBufferSize(sampleRate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (bufSize == AudioRecord.ERROR || bufSize == AudioRecord.ERROR_BAD_VALUE) {
            toast("Microphone unavailable"); return
        }

        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 2)
        } catch (e: Exception) {
            toast("Microphone unavailable"); return
        }

        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release(); audioRecord = null
            toast("Microphone unavailable"); return
        }

        audioRecord?.startRecording()
        isRecording = true
        recordedChunks.clear()

        // Visual feedback
        findViewById<Button>(R.id.btnMic).backgroundTintList =
            resources.getColorStateList(R.color.secondary, null)
        toast("🎤 Recording…")

        recorderThread = Thread {
            val buf = ByteArray(bufSize)
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (isRecording) {
                val n = audioRecord?.read(buf, 0, buf.size) ?: -1
                if (n > 0) {
                    synchronized(recordedChunks) { recordedChunks.add(buf.copyOf(n)) }
                }
            }
        }
        recorderThread?.start()
    }

    private fun stopAudioRecording() {
        isRecording = false
        recorderThread?.join(1500)
        recorderThread = null

        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (_: Exception) {}
        audioRecord = null

        // Reset button appearance
        findViewById<Button>(R.id.btnMic).backgroundTintList = null

        // Combine recorded chunks
        val pcm: ByteArray
        synchronized(recordedChunks) {
            val total = recordedChunks.sumOf { it.size }
            if (total < 800) {
                recordedChunks.clear()
                runOnUiThread { toast("Too short — try again") }
                return
            }
            pcm = ByteArray(total)
            var off = 0
            for (c in recordedChunks) {
                System.arraycopy(c, 0, pcm, off, c.size); off += c.size
            }
            recordedChunks.clear()
        }

        sendAudioToAsr(pcm)
    }

    private fun sendAudioToAsr(pcm: ByteArray) {
        val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
        etMessage.hint = "Transcribing… 🎙️"
        Thread {
            try {
                val text = CharacterApi.asr(token, b64)
                runOnUiThread {
                    etMessage.hint = "Chat with Yui 🌸"
                    if (!text.isNullOrBlank()) {
                        etMessage.setText(text.trim())
                        etMessage.setSelection(etMessage.text.length)
                    } else {
                        toast("Didn't catch that — try again")
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    etMessage.hint = "Chat with Yui 🌸"
                    toast("ASR failed — try again")
                }
            }
        }.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_RECORD_AUDIO
            && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toast("Permission granted — hold mic to speak")
        }
    }

    companion object {
        private const val REQUEST_RECORD_AUDIO = 1001
        private var firstSessionAfterColdStart = true // reset only when process restarts
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---- Session sidebar adapter (multi-select delete + pin) -------------

    private inner class SessionAdapter : RecyclerView.Adapter<SessionAdapter.VH>() {

        private var rawSessions = listOf<CharacterSession>()
        private var selectionMode = false
        private val selectedIds = mutableSetOf<Long>()
        private val pinnedIds = mutableSetOf<Long>() // local pin state

        val selectedCount: Int get() = selectedIds.size

        fun setSessions(list: List<CharacterSession>) {
            rawSessions = list; notifyDataSetChanged()
        }

        /** Sorted: pinned first, then by updatedAt descending. */
        private fun sortedSessions(): List<CharacterSession> {
            return rawSessions.sortedWith(compareByDescending<CharacterSession> { pinnedIds.contains(it.id) }
                .thenByDescending { it.updatedAt })
        }

        fun toggleSelectionMode() {
            selectionMode = !selectionMode
            if (!selectionMode) selectedIds.clear()
            notifyDataSetChanged()
            updateSelectionUI()
            // Update trash icon tint
            val trash = findViewById<ImageButton>(R.id.btnTrash)
            trash.imageTintList = resources.getColorStateList(
                if (selectionMode) R.color.error else R.color.text_hint, null)
        }

        fun exitSelectionMode() {
            if (!selectionMode) return
            selectionMode = false
            selectedIds.clear()
            notifyDataSetChanged()
            updateSelectionUI()
            findViewById<ImageButton>(R.id.btnTrash).imageTintList =
                resources.getColorStateList(R.color.text_hint, null)
        }

        private fun updateSelectionUI() {
            findViewById<View>(R.id.layoutSelectionActions).visibility =
                if (selectionMode) View.VISIBLE else View.GONE
        }

        fun deleteSelected() {
            val ids = selectedIds.toList()
            if (ids.isEmpty()) return
            Thread {
                var count = 0
                for (id in ids) {
                    try { CharacterApi.deleteSession(token, id); count++ } catch (_: Exception) {}
                }
                runOnUiThread {
                    pinnedIds.removeAll(ids.toSet())
                    selectedIds.clear()
                    exitSelectionMode()
                    loadSessions()
                    resetCurrentIfDeleted(ids)
                    toast("Deleted $count chat(s)")
                }
            }.start()
        }

        fun deleteAllSessions() {
            if (rawSessions.isEmpty()) return
            Thread {
                var count = 0
                for (s in rawSessions) {
                    try { CharacterApi.deleteSession(token, s.id); count++ } catch (_: Exception) {}
                }
                runOnUiThread {
                    pinnedIds.clear()
                    selectedIds.clear()
                    exitSelectionMode()
                    loadSessions()
                    currentSessionId = null
                    tvSessionTitle.text = "New Chat"
                    adapter.setMessages(emptyList())
                    etMessage.hint = "Chat with Yui 🌸"
                    toast("Deleted $count chat(s)")
                }
            }.start()
        }

        private fun resetCurrentIfDeleted(ids: List<Long>) {
            if (currentSessionId != null && ids.contains(currentSessionId!!)) {
                currentSessionId = null
                tvSessionTitle.text = "New Chat"
                adapter.setMessages(emptyList())
                etMessage.hint = "Chat with Yui 🌸"
            }
        }

        /** Called from onBindViewHolder via post() to avoid RecyclerView layout crash. */
        private fun toggleSelection(id: Long) {
            if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
            notifyDataSetChanged()
            updateSelectionUI()
            if (selectedIds.isEmpty()) exitSelectionMode()
        }

        private fun togglePin(id: Long) {
            if (pinnedIds.contains(id)) pinnedIds.remove(id) else pinnedIds.add(id)
            notifyDataSetChanged()
        }

        private fun isPinned(id: Long) = pinnedIds.contains(id)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(layoutInflater.inflate(R.layout.item_character_session, parent, false))
        }

        override fun onBindViewHolder(holder: VH, pos: Int) {
            val sorted = sortedSessions()
            if (pos >= sorted.size) return
            val s = sorted[pos]
            val pinned = isPinned(s.id)

            holder.title.text = s.title
            holder.meta.text = "${s.mode} · ${s.messageCount} msgs" + if (pinned) " · 📌" else ""

            if (selectionMode) {
                holder.checkbox.visibility = View.VISIBLE
                holder.checkbox.setOnCheckedChangeListener(null)
                holder.checkbox.isChecked = selectedIds.contains(s.id)
                // Use post() to avoid IllegalStateException during layout
                holder.checkbox.setOnCheckedChangeListener { _, _ ->
                    holder.itemView.post { toggleSelection(s.id) }
                }
                holder.itemView.setOnClickListener {
                    holder.itemView.post { toggleSelection(s.id) }
                }
                holder.itemView.setOnLongClickListener(null)
            } else {
                holder.checkbox.visibility = View.GONE
                holder.checkbox.setOnCheckedChangeListener(null)
                holder.itemView.setOnClickListener {
                    switchToSession(s.id, s.title); drawer.closeDrawer(Gravity.START)
                }
                holder.itemView.setOnLongClickListener {
                    val pinLabel = if (isPinned(s.id)) "Unpin" else "Pin to Top"
                    AlertDialog.Builder(this@CharacterChatActivity)
                        .setTitle(s.title)
                        .setItems(arrayOf(pinLabel, "Delete", "Cancel")) { _, which ->
                            when (which) {
                                0 -> togglePin(s.id)
                                1 -> {
                                    AlertDialog.Builder(this@CharacterChatActivity)
                                        .setTitle("Delete Chat")
                                        .setMessage("Delete \"${s.title}\"?")
                                        .setPositiveButton("Delete") { _, _ ->
                                            Thread {
                                                try { CharacterApi.deleteSession(token, s.id)
                                                    runOnUiThread {
                                                        pinnedIds.remove(s.id)
                                                        loadSessions()
                                                        resetCurrentIfDeleted(listOf(s.id))
                                                    }
                                                } catch (e: Exception) {
                                                    runOnUiThread { toast("Delete failed") }
                                                }
                                            }.start()
                                        }
                                        .setNegativeButton("Cancel", null).show()
                                }
                            }
                        }.show()
                    true
                }
            }
        }

        override fun getItemCount() = rawSessions.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tvSessionItemTitle)
            val meta: TextView = view.findViewById(R.id.tvSessionItemMeta)
            val checkbox: android.widget.CheckBox = view.findViewById(R.id.cbSessionSelect)
        }
    }
}
