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

        findViewById<Button>(R.id.btnSend).setOnClickListener { sendMessage() }

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
        findViewById<Button>(R.id.btnSessions).setOnClickListener {
            loadSessions(); drawer.openDrawer(Gravity.START)
        }
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

        createNewSession()
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
        Thread {
            try {
                val s = CharacterApi.createSession(token, currentMode)
                runOnUiThread {
                    currentSessionId = s.id
                    tvSessionTitle.text = "New Chat"
                    adapter.setMessages(emptyList())
                    etMessage.hint = "Chat with Yui 🌸"
                    drawer.closeDrawer(Gravity.START)
                }
            } catch (e: Exception) {
                runOnUiThread { toast("Failed to create session") }
            }
        }.start()
    }

    private fun switchToSession(sessionId: Long, title: String) {
        currentSessionId = sessionId
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
                    recycler.scrollToPosition(adapter.itemCount - 1)
                }
            } catch (e: Exception) { /* empty */ }
        }.start()
    }

    private fun sendMessage() {
        val text = etMessage.text.toString().trim()
        if (text.isEmpty()) return
        etMessage.text.clear()
        etMessage.hint = "Type a message…"
        findViewById<Button>(R.id.btnSend).isEnabled = false

        // Show user message immediately
        adapter.addUserMessage(text)
        recycler.scrollToPosition(adapter.lastIndex)

        // Show loading bubble for AI
        adapter.showLoading()
        recycler.scrollToPosition(adapter.lastIndex)

        Thread {
            try {
                val resp = CharacterApi.chat(token, text, currentMode, currentSessionId)
                runOnUiThread {
                    currentSessionId = resp.sessionId
                    adapter.updateLoadingReply(resp.reply, resp.emotion, resp.tools)
                    live2dView.setEmotion(resp.emotion)
                    recycler.scrollToPosition(adapter.lastIndex)
                    // Play TTS with mouth sync
                    if (voiceEnabled) speakResponse(resp.reply, resp.emotion)
                    findViewById<Button>(R.id.btnSend).isEnabled = true

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
                        findViewById<Button>(R.id.btnSend).isEnabled = true
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    adapter.updateLoadingReply("Network error. Please try again.", "confused")
                    findViewById<Button>(R.id.btnSend).isEnabled = true
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

    companion object { private const val REQUEST_RECORD_AUDIO = 1001 }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    // ---- Session sidebar adapter -------------------------------------------

    private inner class SessionAdapter : RecyclerView.Adapter<SessionAdapter.VH>() {

        private var sessions = listOf<CharacterSession>()

        fun setSessions(list: List<CharacterSession>) {
            sessions = list; notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(layoutInflater.inflate(R.layout.item_character_session, parent, false))
        }

        override fun onBindViewHolder(holder: VH, pos: Int) {
            val s = sessions[pos]
            holder.title.text = s.title
            holder.meta.text = "${s.mode} · ${s.messageCount} msgs"
            holder.itemView.setOnClickListener {
                switchToSession(s.id, s.title); drawer.closeDrawer(Gravity.START)
            }
            holder.itemView.setOnLongClickListener {
                AlertDialog.Builder(this@CharacterChatActivity)
                    .setTitle("Delete Chat")
                    .setMessage("Delete \"${s.title}\"?")
                    .setPositiveButton("Delete") { _, _ ->
                        Thread {
                            try {
                                CharacterApi.deleteSession(token, s.id)
                                runOnUiThread {
                                    loadSessions()
                                    if (s.id == currentSessionId) {
                                        currentSessionId = null
                                        tvSessionTitle.text = "New Chat"
                                        adapter.setMessages(emptyList())
                                        etMessage.hint = "Chat with Yui 🌸"
                                    }
                                }
                            } catch (e: Exception) {
                                runOnUiThread { toast("Delete failed") }
                            }
                        }.start()
                    }
                    .setNegativeButton("Cancel", null).show()
                true
            }
        }

        override fun getItemCount() = sessions.size

        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.tvSessionItemTitle)
            val meta: TextView = view.findViewById(R.id.tvSessionItemMeta)
        }
    }
}
