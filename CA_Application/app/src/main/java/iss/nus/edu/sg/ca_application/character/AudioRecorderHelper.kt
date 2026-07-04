// Author: Wang Songyu, Liu Yu, Xia Zihang
package iss.nus.edu.sg.ca_application.character

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Base64
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Shared ASR audio recorder used by MainActivity and CharacterChatActivity.
 * Records 16kHz mono PCM, Base64-encodes, and calls CharacterApi.asr().
 */
class AudioRecorderHelper(
    private val onResult: (String?) -> Unit,
    private val onStatus: (String) -> Unit,
    private val tokenProvider: () -> String,
    private val asrLanguage: () -> String
) {
    companion object {
        const val REQUEST_AUDIO = 2001
        private const val SAMPLE_RATE = 16000
        private const val MIN_CHUNK_BYTES = 800
        private const val JOIN_TIMEOUT_MS = 1500L
    }

    private var audioRecord: AudioRecord? = null
    private var isRecording = false
    private var recorderThread: Thread? = null
    private val chunks = mutableListOf<ByteArray>()

    fun startRecording(context: Context) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                (context as? androidx.appcompat.app.AppCompatActivity)!!,
                arrayOf(Manifest.permission.RECORD_AUDIO), REQUEST_AUDIO)
            return
        }
        if (isRecording) return

        val bufSize = AudioRecord.getMinBufferSize(SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (bufSize == AudioRecord.ERROR || bufSize == AudioRecord.ERROR_BAD_VALUE) {
            onStatus("Microphone unavailable"); return
        }
        try {
            audioRecord = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufSize * 2)
        } catch (e: Exception) { onStatus("Microphone unavailable"); return }
        if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord?.release(); audioRecord = null; return
        }

        audioRecord?.startRecording()
        isRecording = true
        chunks.clear()
        onStatus("Recording")

        recorderThread = Thread {
            val buf = ByteArray(bufSize)
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_URGENT_AUDIO)
            while (isRecording) {
                val n = audioRecord?.read(buf, 0, buf.size) ?: -1
                if (n > 0) synchronized(chunks) { chunks.add(buf.copyOf(n)) }
            }
        }
        recorderThread?.start()
    }

    fun stopRecording() {
        isRecording = false
        recorderThread?.join(JOIN_TIMEOUT_MS)
        recorderThread = null
        try { audioRecord?.stop(); audioRecord?.release() } catch (_: Exception) {}
        audioRecord = null

        val pcm: ByteArray
        synchronized(chunks) {
            val total = chunks.sumOf { it.size }
            if (total < MIN_CHUNK_BYTES) { chunks.clear(); onStatus("Too short"); return }
            pcm = ByteArray(total)
            var off = 0
            for (c in chunks) { System.arraycopy(c, 0, pcm, off, c.size); off += c.size }
            chunks.clear()
        }

        val b64 = Base64.encodeToString(pcm, Base64.NO_WRAP)
        Thread {
            try {
                val text = iss.nus.edu.sg.ca_application.network.CharacterApi.asr(
                    tokenProvider(), b64, asrLanguage())
                onResult(text)
            } catch (e: Exception) {
                onResult(null)
            }
        }.start()
    }

    fun cleanup() {
        isRecording = false
        recorderThread?.interrupt()
        recorderThread = null
        audioRecord?.apply {
            try { stop() } catch (_: Exception) {}
            try { release() } catch (_: Exception) {}
        }
        audioRecord = null
    }
}
