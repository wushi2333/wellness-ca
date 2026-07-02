// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.network

import iss.nus.edu.sg.ca_application.model.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object CharacterApi {

    private fun conn(path: String, method: String, token: String): HttpURLConnection {
        val c = URL("$BASE_URL$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.connectTimeout = 30000
        c.readTimeout = 30000
        c.setRequestProperty("Content-Type", "application/json")
        c.setRequestProperty("X-API-Token", API_GATEWAY_TOKEN)
        if (token.isNotEmpty()) c.setRequestProperty("Authorization", "Bearer $token")
        c.doInput = true
        if (method != "GET") c.doOutput = true
        return c
    }

    private fun body(c: HttpURLConnection, json: String) {
        c.outputStream.use {
            OutputStreamWriter(it, "UTF-8").apply { write(json); flush() }
        }
    }

    private fun ok(c: HttpURLConnection) = c.responseCode in 200..299

    fun getSessions(token: String): List<CharacterSession> {
        val list = mutableListOf<CharacterSession>()
        val c = conn("/character/sessions", "GET", token)
        if (ok(c)) {
            val arr = JSONArray(c.inputStream.bufferedReader().readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(CharacterSession(o.getLong("id"), o.getString("title"),
                    o.getString("mode"), o.getInt("messageCount"), o.getString("updatedAt")))
            }
        } else throw ApiException(c.responseCode, c.errorStream?.bufferedReader()?.readText() ?: "")
        c.disconnect()
        return list
    }

    fun createSession(token: String, mode: String): CharacterSession {
        val c = conn("/character/sessions", "POST", token)
        body(c, JSONObject().apply { put("mode", mode) }.toString())
        if (ok(c)) {
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            c.disconnect()
            return CharacterSession(o.getLong("id"), o.getString("title"), o.getString("mode"), 0, "")
        }
        throw ApiException(c.responseCode, c.errorStream?.bufferedReader()?.readText() ?: "")
    }

    fun deleteSession(token: String, id: Long) {
        val c = conn("/character/sessions/$id", "DELETE", token)
        if (!ok(c)) throw ApiException(c.responseCode, c.errorStream?.bufferedReader()?.readText() ?: "")
        c.disconnect()
    }

    fun getMessages(token: String, sessionId: Long): List<CharacterMessage> {
        val list = mutableListOf<CharacterMessage>()
        val c = conn("/character/sessions/$sessionId/messages", "GET", token)
        if (ok(c)) {
            val arr = JSONArray(c.inputStream.bufferedReader().readText())
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val tools = if (o.has("tools") && !o.isNull("tools")) {
                    val tArr = o.getJSONArray("tools")
                    (0 until tArr.length()).map { tArr.getString(it) }
                } else null
                list.add(CharacterMessage(o.getLong("id"), o.getString("role"),
                    o.getString("content"), o.optString("emotion", ""), o.getString("createdAt"), tools))
            }
        } else throw ApiException(c.responseCode, c.errorStream?.bufferedReader()?.readText() ?: "")
        c.disconnect()
        return list
    }

    fun chat(token: String, message: String, mode: String, sessionId: Long?): CharacterChatResponse {
        val c = conn(if (mode == "agent") "/character/agent" else "/character/chat", "POST", token)
        val json = JSONObject().apply {
            put("message", message); put("mode", mode)
            if (sessionId != null) put("sessionId", sessionId)
        }
        body(c, json.toString())
        if (ok(c)) {
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            c.disconnect()
            val intent = if (o.has("intent") && !o.isNull("intent")) {
                val i = o.getJSONObject("intent")
                mapOf("action" to i.getString("action"), "target" to i.getString("target"))
            } else null
            val tools = if (o.has("tools") && !o.isNull("tools")) {
                val arr = o.getJSONArray("tools")
                (0 until arr.length()).map { arr.getString(it) }
            } else null
            return CharacterChatResponse(o.getString("reply"), o.getString("emotion"),
                o.getLong("sessionId"), intent, tools)
        }
        throw ApiException(c.responseCode, c.errorStream?.bufferedReader()?.readText() ?: "")
    }

    fun asr(token: String, base64Audio: String): String? {
        val c = conn("/character/asr", "POST", token)
        val json = JSONObject().apply { put("audio", base64Audio) }
        body(c, json.toString())
        if (ok(c)) {
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            c.disconnect()
            return o.optString("text", null)
        }
        c.disconnect()
        return null
    }
}
