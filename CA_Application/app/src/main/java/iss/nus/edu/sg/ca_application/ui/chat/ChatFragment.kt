// Author: Huang Qianer, Xia Zihang
package iss.nus.edu.sg.ca_application.ui.chat

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.view.*
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.applyTopInset
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.character.CharacterChatAdapter
import iss.nus.edu.sg.ca_application.character.VolcanoTtsService
import iss.nus.edu.sg.ca_application.live2d.Live2DCharacterView
import iss.nus.edu.sg.ca_application.model.CharacterMessage
import iss.nus.edu.sg.ca_application.model.CharacterSession
import iss.nus.edu.sg.ca_application.network.ApiException
import iss.nus.edu.sg.ca_application.network.ApiClient
import iss.nus.edu.sg.ca_application.network.CacheManager
import iss.nus.edu.sg.ca_application.network.CharacterApi
import iss.nus.edu.sg.ca_application.util.onClickDebounced


class ChatFragment : Fragment() {

    companion object {
        private const val MIN_AUDIO_CHUNK_BYTES = 800
        private const val RECORDER_JOIN_TIMEOUT_MS = 1500L
        private const val TITLE_POLL_INTERVAL_MS = 2000L
    }

    private lateinit var token: String
    private var currentSessionId: Long? = null
    private var currentMode = "chat"
    private val adapter = CharacterChatAdapter(showEmotion = true)
    private lateinit var recycler: RecyclerView
    private lateinit var etMessage: EditText
    private lateinit var live2dView: Live2DCharacterView
    private lateinit var btnMode: Button
    private lateinit var btnVoice: Button
    private lateinit var tvSessionTitle: TextView
    private lateinit var drawer: DrawerLayout
    private lateinit var sessionRecycler: RecyclerView
    private val sessionAdapter = SessionAdapter()
    private val handler = Handler(Looper.getMainLooper())
    private val asrLanguage: String get() {
        val appLocale = iss.nus.edu.sg.ca_application.SettingsActivity.getSavedLocale(requireContext())
        val lang = appLocale?.language ?: java.util.Locale.getDefault().language
        return if (lang == "zh") "zh-CN" else "en-US"
    }
    private var voiceEnabled = true
    private var ttsMediaPlayer: android.media.MediaPlayer? = null
    private var ttsAnimThread: Thread? = null

    // ASR permission launcher
    private val requestAudioPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> if (granted) @Suppress("MissingPermission") startRecording() else toast("Microphone permission required") }

    // ASR
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recorderThread: Thread? = null
    private val recordedChunks = mutableListOf<ByteArray>()
    private var titlePollRunnable: Runnable? = null

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_chat, c, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        token = TokenManager.getToken(requireContext())
        drawer = view.findViewById(R.id.drawerLayout)
        live2dView = view.findViewById(R.id.live2dView)
        recycler = view.findViewById(R.id.chatRecycler)
        etMessage = view.findViewById(R.id.etChatMessage)
        btnMode = view.findViewById(R.id.btnModeToggle)
        btnVoice = view.findViewById(R.id.btnToggleVoice)
        tvSessionTitle = view.findViewById(R.id.tvSessionTitle)

        recycler.layoutManager = LinearLayoutManager(requireContext()).apply { stackFromEnd = true }
        recycler.adapter = adapter

        // Sidebar header must sit below status bar
        view.findViewById<LinearLayout>(R.id.sidebarHeader).applyTopInset()

        // Scroll to latest when keyboard opens and shrinks the RecyclerView
        ViewCompat.setOnApplyWindowInsetsListener(recycler) { v, insets ->
            if (insets.isVisible(WindowInsetsCompat.Type.ime()) && adapter.itemCount > 0) {
                v.post { recycler.scrollToPosition(adapter.lastIndex) }
            }
            insets
        }

        sessionRecycler = view.findViewById(R.id.sessionRecycler)
        sessionRecycler.layoutManager = LinearLayoutManager(requireContext())
        sessionRecycler.adapter = sessionAdapter

        // ── Buttons ──
        view.findViewById<ImageButton>(R.id.btnChatSend).setOnClickListener { sendMessage() }
        val micBtn = view.findViewById<ImageButton>(R.id.btnChatMic)
        micBtn.setColorFilter(android.graphics.Color.parseColor("#94A3B8"))
        micBtn.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                        requestAudioPermission.launch(Manifest.permission.RECORD_AUDIO)
                    } else {
                        micBtn.setColorFilter(android.graphics.Color.parseColor("#22C55E"))
                        startRecording()
                    }
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    micBtn.setColorFilter(android.graphics.Color.parseColor("#94A3B8"))
                    if (isRecording) stopRecording()
                    true
                }
                else -> false
            }
        }
        // Preload RAG in background (fire-and-forget)
        Thread { try { CharacterApi.preloadRag(token) } catch (_: Exception) {} }.start()

        btnMode.setOnClickListener { toggleMode() }
        btnVoice.onClickDebounced {
            voiceEnabled = !voiceEnabled
            btnVoice.setBackgroundResource(if (voiceEnabled) R.drawable.ic_volume_on else R.drawable.ic_volume_off)
            if (!voiceEnabled) {
                stopRecording()
                stopTts()
            }
        }
        view.findViewById<View>(R.id.btnSessions).setOnClickListener { loadSessions(); drawer.openDrawer(androidx.core.view.GravityCompat.END) }
        view.findViewById<View>(R.id.btnNewChat).setOnClickListener { createNewSession() }
        view.findViewById<View>(R.id.btnTrash).setOnClickListener { sessionAdapter.toggleSelectionMode() }
        view.findViewById<View>(R.id.btnDeleteSelected).setOnClickListener {
            val n = sessionAdapter.selectedCount
            if (n > 0) AlertDialog.Builder(requireContext()).setTitle("Delete Selected")
                .setMessage("Delete $n chat(s)?").setPositiveButton("Delete") { _, _ -> sessionAdapter.deleteSelected() }
                .setNegativeButton("Cancel", null).show()
        }
        view.findViewById<View>(R.id.btnDeleteAll).setOnClickListener {
            AlertDialog.Builder(requireContext()).setTitle("Delete All")
                .setMessage("Delete ALL chats? This cannot be undone.")
                .setPositiveButton("Delete All") { _, _ -> sessionAdapter.deleteAllSessions() }
                .setNegativeButton("Cancel", null).show()
        }
        drawer.addDrawerListener(object : DrawerLayout.SimpleDrawerListener() {
            override fun onDrawerClosed(v: View) { sessionAdapter.exitSelectionMode() }
        })

        // Loading overlay for cold start (first time Live2D model loads)
        val isColdStart = activity != null && !live2dView.isModelCached()
        val loadingOverlay: View? = if (isColdStart) {
            View.inflate(requireContext(), R.layout.overlay_loading, null).also {
                (view as? ViewGroup)?.addView(it, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT))
            }
        } else null

        if (isColdStart) {
            iss.nus.edu.sg.ca_application.live2d.LAppMinimumLive2DManager.setOnReadyStatic {
                activity?.runOnUiThread {
                    loadingOverlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
                }
            }
        }

        // Welcome
        val username = TokenManager.getUsername(requireContext()).ifEmpty { "User" }
        adapter.addAssistantMessage(getString(R.string.yui_welcome, username), null)
    }

    // ── Mode ──────────────────────────────────────────────────────

    private fun toggleMode() {
        currentMode = if (currentMode == "chat") "agent" else "chat"
        val isAgent = currentMode == "agent"
        btnMode.text = if (isAgent) getString(R.string.agent) else getString(R.string.chat)
        btnMode.setBackgroundResource(if (isAgent) R.drawable.bg_btn_agent else R.drawable.bg_btn_chat)
        btnMode.setTextColor(ContextCompat.getColor(requireContext(),
            if (isAgent) R.color.text_primary else R.color.on_primary))
        live2dView.setModeExpression(currentMode)
    }

    private fun refreshModeColor() {
        val isAgent = currentMode == "agent"
        btnMode.setBackgroundResource(if (isAgent) R.drawable.bg_btn_agent else R.drawable.bg_btn_chat)
        btnMode.setTextColor(ContextCompat.getColor(requireContext(),
            if (isAgent) R.color.text_primary else R.color.on_primary))
    }

    // ── Sessions ───────────────────────────────────────────────────

    private fun loadSessions() {
        Thread {
            try {
                val sessions = CharacterApi.getSessions(token)
                activity?.runOnUiThread { sessionAdapter.setSessions(sessions) }
            } catch (_: Exception) {}
        }.start()
    }

    private fun createNewSession() {
        if (adapter.lastIndex < 0) { drawer.closeDrawer(androidx.core.view.GravityCompat.END); return }
        currentSessionId = null
        tvSessionTitle.text = "New Chat"
        adapter.setMessages(emptyList())
        etMessage.hint = "Chat with Yui…"
        drawer.closeDrawer(androidx.core.view.GravityCompat.END)
    }

    private fun switchToSession(sessionId: Long, title: String) {
        currentSessionId = sessionId
        tvSessionTitle.text = title
        adapter.setMessages(emptyList())
        loadMessages(sessionId)
    }

    private fun loadMessages(sessionId: Long) {
        Thread {
            try {
                val msgs = CharacterApi.getMessages(token, sessionId)
                activity?.runOnUiThread {
                    adapter.setMessages(msgs)
                    recycler.scrollToPosition(adapter.itemCount - 1)
                }
            } catch (_: Exception) {}
        }.start()
    }

    private fun resetCurrentIfDeleted(ids: List<Long>) {
        if (currentSessionId != null && ids.contains(currentSessionId!!)) {
            currentSessionId = null; tvSessionTitle.text = "New Chat"
            adapter.setMessages(emptyList())
        }
    }

    // ── ASR ────────────────────────────────────────────────────────
    @androidx.annotation.RequiresPermission(android.Manifest.permission.RECORD_AUDIO)
    private fun startRecording() {
        if (isRecording) return
        val rate = 16000
        val bufSize = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (bufSize == AudioRecord.ERROR || bufSize == AudioRecord.ERROR_BAD_VALUE) { toast("Mic unavailable"); return }
        try { audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 2) }
        catch (e: Exception) { toast("Mic unavailable"); return }
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            toast("Mic init failed"); audioRecord?.release(); audioRecord = null; return
        }
        audioRecord?.startRecording(); isRecording = true; recordedChunks.clear(); toast("🎤 Recording…")
        recorderThread = Thread {
            val buf = ByteArray(bufSize)
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (isRecording) { val n = audioRecord?.read(buf, 0, buf.size) ?: -1; if (n > 0) synchronized(recordedChunks) { recordedChunks.add(buf.copyOf(n)) } }
        }; recorderThread?.start()
    }

    private fun stopRecording() {
        if (!isRecording) return
        isRecording = false; recorderThread?.join(RECORDER_JOIN_TIMEOUT_MS); recorderThread = null
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}; audioRecord = null
        val pcm: ByteArray
        synchronized(recordedChunks) {
            val total = recordedChunks.sumOf { it.size }
            if (total < MIN_AUDIO_CHUNK_BYTES) { recordedChunks.clear(); toast("Too short"); return }
            pcm = ByteArray(total); var off = 0
            for (c in recordedChunks) { System.arraycopy(c, 0, pcm, off, c.size); off += c.size }
            recordedChunks.clear()
        }; sendAudio(pcm)
    }

    private fun sendAudio(pcm: ByteArray) {
        val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP); etMessage.hint = "Transcribing…"
        Thread {
            try {
                val text = CharacterApi.asr(token, b64, asrLanguage)
                activity?.runOnUiThread {
                    etMessage.hint = "Chat with Yui…"
                    if (!text.isNullOrBlank()) {
                        etMessage.setText(text.trim()); etMessage.setSelection(etMessage.text.length)
                    } else {
                        toast("Didn't catch that ($asrLanguage)")
                    }
                }
            } catch (_: Exception) { activity?.runOnUiThread { etMessage.hint = "Chat with Yui…"; toast("ASR failed") } }
        }.start()
    }

    // ── Chat + TTS ─────────────────────────────────────────────────

    private fun sendMessage() {
        val text = etMessage.text.toString().trim(); if (text.isEmpty()) return
        etMessage.text.clear(); view?.findViewById<ImageButton>(R.id.btnChatSend)?.isEnabled = false
        adapter.addUserMessage(text); adapter.showLoading(); recycler.scrollToPosition(adapter.lastIndex)
        Thread {
            try {
                if (currentSessionId == null) { val s = CharacterApi.createSession(token, currentMode); currentSessionId = s.id }
                val resp = CharacterApi.chat(token, text, currentMode, currentSessionId)
                activity?.runOnUiThread {
                    currentSessionId = resp.sessionId
                    adapter.updateLoadingReply(resp.reply, resp.emotion, resp.tools)
                    live2dView.setEmotion(resp.emotion)
                    view?.findViewById<ImageButton>(R.id.btnChatSend)?.isEnabled = true
                    recycler.scrollToPosition(adapter.lastIndex)
                    if (voiceEnabled) speakResponse(resp.reply, resp.emotion)
                    pollSessionTitle(resp.sessionId)
                    resp.intent?.let { handleIntent(it) }
                }
            } catch (e: ApiException) {
                activity?.runOnUiThread { adapter.updateLoadingReply("Something went wrong. Try again?", "confused"); view?.findViewById<ImageButton>(R.id.btnChatSend)?.isEnabled = true }
            } catch (e: Exception) {
                activity?.runOnUiThread { adapter.updateLoadingReply("Network error. Please try again.", "confused"); view?.findViewById<ImageButton>(R.id.btnChatSend)?.isEnabled = true }
            }
        }.start()
    }

    private fun speakResponse(text: String, emotion: String) {
        val model = iss.nus.edu.sg.ca_application.live2d.LAppMinimumLive2DManager.getInstance().getModel(0)
        stopTts()
        Thread {
            val result = VolcanoTtsService.synthesize(text, emotion, token)
            if (result != null) {
                activity?.runOnUiThread {
                    val mp = android.media.MediaPlayer()
                    ttsMediaPlayer = mp
                    ttsAnimThread = VolcanoTtsService.playMp3(requireContext(), result, mp,
                        onMouth = { v -> model?.mouthOpenOverride = v },
                        onComplete = {
                            model?.mouthOpenOverride = 0f
                            ttsMediaPlayer = null; ttsAnimThread = null
                        })
                }
            }
        }.start()
    }

    private fun stopTts() {
        ttsAnimThread?.interrupt(); ttsAnimThread = null
        try { ttsMediaPlayer?.stop(); ttsMediaPlayer?.release() } catch (_: Exception) {}
        ttsMediaPlayer = null
    }

    private fun pollSessionTitle(sessionId: Long) {
        titlePollRunnable?.let { handler.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                Thread {
                    try {
                        val sessions = CharacterApi.getSessions(token)
                        val s = sessions.find { it.id == sessionId }
                        if (s != null && s.title != "New Chat") {
                            activity?.runOnUiThread { if (currentSessionId == sessionId) { tvSessionTitle.text = s.title; sessionAdapter.setSessions(sessions) } }
                            return@Thread
                        }
                        handler.postDelayed(this, TITLE_POLL_INTERVAL_MS)
                    } catch (_: Exception) {}
                }.start()
            }
        }; titlePollRunnable = r; handler.postDelayed(r, TITLE_POLL_INTERVAL_MS)
    }

    // ── Agent intent navigation ────────────────────────────────────

    private fun handleIntent(intent: Map<String, Any?>) {
        val action = intent["action"]?.toString() ?: return
        when (action) {
            "navigate" -> (activity as? iss.nus.edu.sg.ca_application.MainActivity)?.navigateTo(intent["target"]?.toString() ?: return)
            "create_record" -> createRecordFromIntent(intent)
        }
    }

    private fun createRecordFromIntent(intent: Map<String, Any?>) {
        val recordType = intent["recordType"]?.toString() ?: return
        val date = intent["recordDate"]?.toString() ?: android.icu.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault()).format(java.util.Date())
        Thread {
            try {
                if (recordType == "sleep") {
                    ApiClient.createSleepRecord(token,
                        (intent["sleepHours"] as? Number)?.toDouble() ?: 0.0,
                        intent["sleepTime"]?.toString() ?: "",
                        intent["wakeTime"]?.toString() ?: "",
                        (intent["moodScore"] as? Number)?.toInt() ?: 0,
                        date,
                        intent["notes"]?.toString() ?: "Created via agent")
                } else {
                    ApiClient.createExerciseRecord(token,
                        intent["exerciseActivity"]?.toString() ?: "Other",
                        (intent["exerciseDuration"] as? Number)?.toInt() ?: 0,
                        date,
                        intent["notes"]?.toString() ?: "Created via agent")
                }
                activity?.runOnUiThread {
                    CacheManager.invalidate("records")
                    Toast.makeText(requireContext(), "Record saved via agent ✨", Toast.LENGTH_SHORT).show()
                    (activity as? iss.nus.edu.sg.ca_application.MainActivity)?.homeFragment?.loadData()
                }
            } catch (e: Exception) {
                activity?.runOnUiThread {
                    Toast.makeText(requireContext(), "Failed: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    // ── Lifecycle ──────────────────────────────────────────────────

    override fun onResume() { super.onResume(); activity?.let { live2dView.onStart(it) } }
    override fun onPause() { super.onPause(); live2dView.onStop() }

    override fun onDestroyView() {
        super.onDestroyView()
        titlePollRunnable?.let { handler.removeCallbacks(it) }
        isRecording = false; recorderThread?.interrupt(); recorderThread = null
        audioRecord?.apply { try { stop() } catch (_: Exception) {}; try { release() } catch (_: Exception) {} }; audioRecord = null
        stopTts()
        live2dView.onDestroy()
    }

    private fun toast(msg: String) { Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show() }

    // ── Session Adapter ────────────────────────────────────────────

    private inner class SessionAdapter : RecyclerView.Adapter<SessionAdapter.VH>() {
        private var raw = listOf<CharacterSession>()
        private var selectionMode = false
        private val selectedIds = mutableSetOf<Long>()
        private val pinnedIds = mutableSetOf<Long>()
        val selectedCount get() = selectedIds.size

        fun setSessions(list: List<CharacterSession>) { raw = list; notifyDataSetChanged() }
        private fun sorted() = raw.sortedWith(compareByDescending<CharacterSession> { pinnedIds.contains(it.id) }.thenByDescending { it.updatedAt })

        fun toggleSelectionMode() {
            selectionMode = !selectionMode; if (!selectionMode) selectedIds.clear()
            notifyDataSetChanged(); updateSelectionUI()
        }
        fun exitSelectionMode() { if (!selectionMode) return; selectionMode = false; selectedIds.clear(); notifyDataSetChanged(); updateSelectionUI() }
        private fun updateSelectionUI() { view?.findViewById<View>(R.id.layoutSelectionActions)?.visibility = if (selectionMode) View.VISIBLE else View.GONE }

        fun deleteSelected() {
            val ids = selectedIds.toList(); if (ids.isEmpty()) return
            Thread {
                for (id in ids) try { CharacterApi.deleteSession(token, id) } catch (_: Exception) {}
                activity?.runOnUiThread { pinnedIds.removeAll(ids.toSet()); selectedIds.clear(); exitSelectionMode(); loadSessions(); resetCurrentIfDeleted(ids) }
            }.start()
        }
        fun deleteAllSessions() {
            val all = raw.toList(); if (all.isEmpty()) return
            Thread {
                for (s in all) try { CharacterApi.deleteSession(token, s.id) } catch (_: Exception) {}
                activity?.runOnUiThread {
                    pinnedIds.clear(); selectedIds.clear(); exitSelectionMode(); loadSessions()
                    currentSessionId = null; tvSessionTitle.text = "New Chat"; adapter.setMessages(emptyList())
                }
            }.start()
        }

        override fun onCreateViewHolder(p: ViewGroup, vt: Int) = VH(layoutInflater.inflate(R.layout.item_character_session, p, false))
        override fun getItemCount() = raw.size

        override fun onBindViewHolder(h: VH, pos: Int) {
            val sorted = sorted(); if (pos >= sorted.size) return
            val s = sorted[pos]; val pinned = pinnedIds.contains(s.id)
            h.title.text = s.title; h.meta.text = "${s.mode} · ${s.messageCount} msgs" + if (pinned) " · ${getString(R.string.pinned)}" else ""
            if (selectionMode) {
                h.cb.visibility = View.VISIBLE
                h.cb.setOnCheckedChangeListener(null); h.cb.isChecked = selectedIds.contains(s.id)
                h.cb.setOnCheckedChangeListener { _, _ -> h.itemView.post { toggleSel(s.id) } }
                h.itemView.setOnClickListener { h.itemView.post { toggleSel(s.id) } }
                h.itemView.setOnLongClickListener(null)
            } else {
                h.cb.visibility = View.GONE
                h.itemView.setOnClickListener { switchToSession(s.id, s.title); drawer.closeDrawer(androidx.core.view.GravityCompat.END) }
                h.itemView.setOnLongClickListener {
                    AlertDialog.Builder(requireContext()).setTitle(s.title)
                        .setItems(arrayOf(if (pinned) "Unpin" else "Pin to Top", "Delete", "Cancel")) { _, which ->
                            when (which) { 0 -> togglePin(s.id); 1 -> confirmDelete(s) }
                        }.show(); true
                }
            }
        }
        private fun toggleSel(id: Long) { if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id); notifyDataSetChanged(); updateSelectionUI(); if (selectedIds.isEmpty()) exitSelectionMode() }
        private fun togglePin(id: Long) { if (pinnedIds.contains(id)) pinnedIds.remove(id) else pinnedIds.add(id); notifyDataSetChanged() }
        private fun confirmDelete(s: CharacterSession) {
            AlertDialog.Builder(requireContext()).setTitle("Delete Chat").setMessage("Delete \"${s.title}\"?")
                .setPositiveButton("Delete") { _, _ -> Thread { try { CharacterApi.deleteSession(token, s.id); activity?.runOnUiThread { pinnedIds.remove(s.id); loadSessions(); resetCurrentIfDeleted(listOf(s.id)) } } catch (_: Exception) {} }.start() }
                .setNegativeButton("Cancel", null).show()
        }

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val title: TextView = v.findViewById(R.id.tvSessionItemTitle)
            val meta: TextView = v.findViewById(R.id.tvSessionItemMeta)
            val cb: CheckBox = v.findViewById(R.id.cbSessionSelect)
        }
    }
}
