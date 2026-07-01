package iss.nus.edu.sg.ca_application.network

import iss.nus.edu.sg.ca_application.model.AgentHistoryItem
import iss.nus.edu.sg.ca_application.model.AgentRecommendation
import iss.nus.edu.sg.ca_application.model.ToolTrace
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * HTTP calls for the agentic recommendation endpoints.
 *
 * Both methods block — call them from a background thread, never the UI thread.
 * The backend uses snake_case for agent fields (saved_id, created_at) because
 * the Java controller transparently forwards the Python sidecar response;
 * this layer maps to Kotlin camelCase to match the rest of the codebase.
 *
 * Author: Cai Peilin
 */
object AgentApi {

    @Throws(IOException::class)
    fun recommend(bearerToken: String): AgentRecommendation {
        val conn = openAuthenticatedConnection("/agent/recommend", "POST", bearerToken)
        // The body is empty but Spring still needs the method to be POST.
        conn.doOutput = false
        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IOException("Agent /recommend returned $code: $err")
        }
        return parseRecommendation(JSONObject(body))
    }

    @Throws(IOException::class)
    fun history(bearerToken: String, limit: Int = 10): List<AgentHistoryItem> {
        val conn = openAuthenticatedConnection(
            "/agent/recommend/history?limit=$limit",
            "GET",
            bearerToken
        )
        val code = conn.responseCode
        val body = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IOException("Agent /history returned $code: $err")
        }
        val arr = JSONArray(body)
        return List(arr.length()) { i -> parseHistoryItem(arr.getJSONObject(i)) }
    }

    // ---------------------------------------------------------------------
    // JSON → Kotlin
    // ---------------------------------------------------------------------
    private fun parseRecommendation(obj: JSONObject): AgentRecommendation =
        AgentRecommendation(
            recommendation = obj.getString("recommendation"),
            evidence = parseEvidence(obj.getJSONArray("evidence")),
            iterations = obj.getInt("iterations"),
            savedId = obj.getInt("saved_id")
        )

    private fun parseHistoryItem(obj: JSONObject): AgentHistoryItem =
        AgentHistoryItem(
            id = obj.getInt("id"),
            content = obj.getString("content"),
            evidence = parseEvidence(obj.getJSONArray("evidence")),
            iterations = obj.getInt("iterations"),
            createdAt = obj.getString("created_at")
        )

    private fun parseEvidence(arr: JSONArray): List<ToolTrace> =
        List(arr.length()) { i ->
            val o = arr.getJSONObject(i)
            ToolTrace(name = o.getString("name"), summary = o.getString("summary"))
        }
}