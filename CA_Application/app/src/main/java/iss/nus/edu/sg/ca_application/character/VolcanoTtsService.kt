// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.character

import android.content.Context
import android.media.MediaPlayer
import iss.nus.edu.sg.ca_application.network.BASE_URL
import iss.nus.edu.sg.ca_application.network.API_GATEWAY_TOKEN
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter

object VolcanoTtsService {

    data class TtsResult(val mp3Data: ByteArray)

    fun synthesize(text: String, emotion: String, token: String): TtsResult? {
        var conn: HttpURLConnection? = null
        try {
            conn = URL("$BASE_URL/character/tts").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.doOutput = true
            conn.connectTimeout = 15000
            conn.readTimeout = 30000
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("X-API-Token", API_GATEWAY_TOKEN)
            conn.setRequestProperty("Authorization", "Bearer $token")

            conn.outputStream.use {
                OutputStreamWriter(it, "UTF-8").apply {
                    write("{\"text\":\"${text.replace("\"", "\\\"")}\",\"emotion\":\"$emotion\"}")
                    flush()
                }
            }
            if (conn.responseCode != 200) return null
            return TtsResult(conn.inputStream.readBytes())
        } finally {
            conn?.disconnect()
        }
    }

    fun playMp3(ctx: Context, result: TtsResult, onMouth: (Float) -> Unit, onComplete: () -> Unit) {
        val tmp = File.createTempFile("tts", ".mp3", ctx.cacheDir)
        tmp.writeBytes(result.mp3Data)
        val playing = java.util.concurrent.atomic.AtomicBoolean(true)
        val mp = MediaPlayer().apply {
            setDataSource(tmp.absolutePath)
            prepare()
            setOnCompletionListener {
                playing.set(false)
                onMouth(0f)
                onComplete()
                release()
                tmp.delete()
            }
            start()
        }
        // Mouth animation driven by audio playback position
        Thread {
            while (playing.get()) {
                try {
                    val pos = mp.currentPosition.toFloat() / 1000f // seconds
                    // Simulate syllable rhythm: rapid open/close at ~5Hz
                    val v = (Math.sin(pos * Math.PI * 5.0) * 0.5 + 0.5).toFloat() * 0.7f
                    onMouth(v)
                    Thread.sleep(40)
                } catch (_: Exception) { break }
            }
        }.start()
    }
}
