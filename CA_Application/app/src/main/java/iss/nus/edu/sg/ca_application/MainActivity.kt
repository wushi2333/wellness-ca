// Author: Wang Songyu, Liu Yu, Xia Zihang
package iss.nus.edu.sg.ca_application

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.util.Base64
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import iss.nus.edu.sg.ca_application.agentic.RecommendationActivity
import iss.nus.edu.sg.ca_application.auth.LoginActivity
import iss.nus.edu.sg.ca_application.character.AgentPopupAdapter
import iss.nus.edu.sg.ca_application.character.CharacterLoadingActivity
import iss.nus.edu.sg.ca_application.character.VolcanoTtsService
import iss.nus.edu.sg.ca_application.auth.TokenManager
import iss.nus.edu.sg.ca_application.model.*
import iss.nus.edu.sg.ca_application.network.ApiException
import iss.nus.edu.sg.ca_application.network.CharacterApi
import iss.nus.edu.sg.ca_application.wellness.WellnessEntryActivity
import iss.nus.edu.sg.ca_application.wellness.WellnessListActivity

/**
 * Main dashboard — shown after login. Features a floating agent button
 * that opens an agent-mode chat popup (no Live2D) with TTS voice,
 * draggable resize, and dim scrim.
 */
class MainActivity : AppCompatActivity() {

    // Dashboard
    private var agentFab: Button? = null
    private var popupContainer: View? = null
    private var scrim: View? = null

    // Popup chat
    private var popupRecycler: RecyclerView? = null
    private var popupAdapter: AgentPopupAdapter? = null
    private var popupSessionId: Long? = null
    private var etPopupMsg: EditText? = null
    private var voiceEnabled = true // TTS enabled by default

    // Audio recording for ASR in popup
    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recorderThread: Thread? = null
    private val recordedChunks = mutableListOf<ByteArray>()

    // Drag-to-resize state
    private var dragStartY = 0f
    private var dragStartH = 0
    private var popupHeader: View? = null

    private lateinit var token: String

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        token = TokenManager.getToken(this)

        // ---- Dashboard buttons ----
        findViewById<Button>(R.id.btnViewRecords).setOnClickListener {
            startActivity(Intent(this, WellnessListActivity::class.java))
        }
        findViewById<Button>(R.id.btnAddRecord).setOnClickListener {
            startActivity(Intent(this, WellnessEntryActivity::class.java))
        }
        findViewById<Button>(R.id.btnWellnessInsights).setOnClickListener {
            startActivity(Intent(this, RecommendationActivity::class.java))
        }
        findViewById<Button>(R.id.btnCharacterChat).setOnClickListener {
            startActivity(Intent(this, CharacterLoadingActivity::class.java))
        }
        findViewById<Button>(R.id.btnLogout).setOnClickListener {
            TokenManager.clearToken(this)
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        // ---- Scrim (dismiss popup on click) ----
        scrim = findViewById(R.id.scrim)
        scrim?.setOnClickListener { hidePopup() }

        // ---- Agent FAB ----
        agentFab = findViewById(R.id.btnAgentFab)
        popupContainer = findViewById(R.id.agentPopup)
        agentFab?.setOnClickListener { togglePopup() }

        // ---- Popup header buttons ----
        findViewById<Button>(R.id.btnPopupClose).setOnClickListener { hidePopup() }
        findViewById<Button>(R.id.btnPopupClear).setOnClickListener {
            popupAdapter?.clear()
            popupSessionId = null
        }
        findViewById<Button>(R.id.btnPopupFullChat).setOnClickListener {
            hidePopup()
            startActivity(Intent(this, CharacterLoadingActivity::class.java))
        }
        val btnVoice = findViewById<Button>(R.id.btnPopupVoice)
        btnVoice.setOnClickListener {
            voiceEnabled = !voiceEnabled
            btnVoice.text = if (voiceEnabled) "🔊" else "🔇"
        }

        // ---- Popup header drag-to-resize (drag on title area, buttons still clickable) ----
        popupHeader = findViewById(R.id.popupHeader)
        popupHeader?.setOnTouchListener { _, event -> handleHeaderDrag(event) }

        // ---- Popup chat ----
        popupRecycler = findViewById(R.id.popupRecycler)
        popupAdapter = AgentPopupAdapter()
        popupRecycler?.layoutManager = LinearLayoutManager(this).apply { stackFromEnd = true }
        popupRecycler?.adapter = popupAdapter

        etPopupMsg = findViewById(R.id.etPopupMessage)

        findViewById<Button>(R.id.btnPopupSend).setOnClickListener { sendPopupMessage() }
        findViewById<Button>(R.id.btnPopupMic).setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { startPopupRecording(); true }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    if (isRecording) stopPopupRecording(); true
                }
                else -> false
            }
        }

        // Greeting
        popupAdapter?.addAssistantReply(
            "Hi! I'm Yui in agent mode. I can help you navigate the app, " +
            "analyze your wellness data, or answer questions about how to use this app. " +
            "Try asking me anything! ✨", null
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        isRecording = false
        recorderThread?.interrupt()
        audioRecord?.apply {
            try { stop() } catch (_: Exception) {}
            try { release() } catch (_: Exception) {}
        }
        audioRecord = null
    }

    // ---- Popup show/hide -------------------------------------------------

    private fun togglePopup() {
        if (popupContainer?.visibility == View.VISIBLE) hidePopup() else showPopup()
    }

    private fun showPopup() {
        scrim?.visibility = View.VISIBLE
        popupContainer?.apply {
            visibility = View.VISIBLE
            post {
                translationY = height.toFloat()
                animate().translationY(0f).setDuration(250).start()
            }
        }
        agentFab?.visibility = View.GONE
    }

    private fun hidePopup() {
        val h = popupContainer?.height?.toFloat() ?: 1200f
        popupContainer?.animate()
            ?.translationY(h)
            ?.setDuration(200)
            ?.withEndAction { popupContainer?.visibility = View.GONE }
            ?.start()
        scrim?.visibility = View.GONE
        agentFab?.visibility = View.VISIBLE
    }

    // ---- Header drag-to-resize -------------------------------------------

    private var didDrag = false

    private fun handleHeaderDrag(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                dragStartY = event.rawY
                dragStartH = popupContainer?.height ?: 0
                didDrag = false
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = Math.abs(dragStartY - event.rawY)
                if (dy > 10) didDrag = true  // exceeded dead zone
                if (!didDrag) return true
                val newDy = dragStartY - event.rawY  // up = taller
                val minH = dp(200)
                val maxH = (resources.displayMetrics.heightPixels * 0.85).toInt()
                val newH = (dragStartH + newDy.toInt()).coerceIn(minH, maxH)
                popupContainer?.layoutParams = (popupContainer?.layoutParams as? ViewGroup.LayoutParams)
                    ?.apply { height = newH }
                popupContainer?.requestLayout()
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> return true
        }
        return false
    }

    private fun dp(px: Int) = (px * resources.displayMetrics.density).toInt()

    // ---- Chat logic -------------------------------------------------------

    private fun sendPopupMessage() {
        val text = etPopupMsg?.text.toString().trim()
        if (text.isEmpty()) return
        etPopupMsg?.text?.clear()
        findViewById<Button>(R.id.btnPopupSend).isEnabled = false

        popupAdapter?.addUserMsg(text)
        popupAdapter?.addAssistantPlaceholder()
        popupRecycler?.scrollToPosition(popupAdapter!!.lastIndex)

        Thread {
            try {
                val resp = CharacterApi.chat(token, text, "agent", popupSessionId)
                runOnUiThread {
                    popupSessionId = resp.sessionId
                    popupAdapter?.updateAssistantReply(resp.reply, resp.tools)
                    popupRecycler?.scrollToPosition(popupAdapter!!.lastIndex)
                    findViewById<Button>(R.id.btnPopupSend).isEnabled = true
                    // TTS voice output
                    if (voiceEnabled) speakPopupReply(resp.reply, resp.emotion)
                    resp.intent?.let { handlePopupIntent(it) }
                }
            } catch (e: ApiException) {
                runOnUiThread {
                    if (e.code == 401) {
                        TokenManager.clearToken(this)
                        startActivity(Intent(this, LoginActivity::class.java))
                        finish()
                    } else {
                        popupAdapter?.updateAssistantReply("Oops! Something went wrong. Try again?")
                        findViewById<Button>(R.id.btnPopupSend).isEnabled = true
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    popupAdapter?.updateAssistantReply("Network error. Please try again.")
                    findViewById<Button>(R.id.btnPopupSend).isEnabled = true
                }
            }
        }.start()
    }

    /** Play agent reply via TTS (no mouth sync — no Live2D in popup). */
    private fun speakPopupReply(text: String, emotion: String) {
        Thread {
            val result = VolcanoTtsService.synthesize(text, emotion, token)
            if (result != null) {
                runOnUiThread {
                    VolcanoTtsService.playMp3(
                        this, result,
                        onMouth = {},  // no Live2D, no mouth sync
                        onComplete = {}
                    )
                }
            }
        }.start()
    }

    /** Handle navigation intent from agent response in popup. */
    private fun handlePopupIntent(intent: Map<String, String>) {
        if (intent["action"] != "navigate") return
        hidePopup()
        when (intent["target"]) {
            "wellness_list" -> startActivity(Intent(this, WellnessListActivity::class.java))
            "wellness_entry" -> startActivity(Intent(this, WellnessEntryActivity::class.java))
            "wellness_insights" -> startActivity(Intent(this, RecommendationActivity::class.java))
            "dashboard" -> { /* already here */ }
        }
    }

    // ---- ASR (same as CharacterChatActivity) ------------------------------

    private fun startPopupRecording() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        if (isRecording) return

        val rate = 16000
        val bufSize = AudioRecord.getMinBufferSize(rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (bufSize == AudioRecord.ERROR || bufSize == AudioRecord.ERROR_BAD_VALUE) {
            toast("Microphone unavailable"); return
        }
        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, rate,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 2)
        } catch (e: Exception) { toast("Microphone unavailable"); return }
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release(); audioRecord = null; return
        }

        audioRecord?.startRecording()
        isRecording = true
        recordedChunks.clear()
        toast("🎤 Recording…")

        recorderThread = Thread {
            val buf = ByteArray(bufSize)
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (isRecording) {
                val n = audioRecord?.read(buf, 0, buf.size) ?: -1
                if (n > 0) synchronized(recordedChunks) { recordedChunks.add(buf.copyOf(n)) }
            }
        }
        recorderThread?.start()
    }

    private fun stopPopupRecording() {
        isRecording = false
        recorderThread?.join(1500)
        recorderThread = null
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null

        val pcm: ByteArray
        synchronized(recordedChunks) {
            val total = recordedChunks.sumOf { it.size }
            if (total < 800) { recordedChunks.clear(); toast("Too short"); return }
            pcm = ByteArray(total)
            var off = 0
            for (c in recordedChunks) { System.arraycopy(c, 0, pcm, off, c.size); off += c.size }
            recordedChunks.clear()
        }
        sendPopupAudio(pcm)
    }

    private fun sendPopupAudio(pcm: ByteArray) {
        val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
        etPopupMsg?.hint = "Transcribing…"
        Thread {
            try {
                val text = CharacterApi.asr(token, b64)
                runOnUiThread {
                    etPopupMsg?.hint = "Ask Yui…"
                    if (!text.isNullOrBlank()) {
                        etPopupMsg?.setText(text.trim())
                        etPopupMsg?.setSelection(etPopupMsg!!.text.length)
                    } else toast("Didn't catch that")
                }
            } catch (e: Exception) {
                runOnUiThread { etPopupMsg?.hint = "Ask Yui…"; toast("ASR failed") }
            }
        }.start()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_AUDIO && grantResults.isNotEmpty()
            && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            toast("Permission granted — hold mic to speak")
        }
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
    }

    companion object { private const val REQUEST_AUDIO = 2001 }
}
