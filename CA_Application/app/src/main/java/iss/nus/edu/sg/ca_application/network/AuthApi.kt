package iss.nus.edu.sg.ca_application.network

import org.json.JSONObject
import java.io.IOException

/**
 * Login helper used by the agentic feature when no JWT is present.
 *
 * In integrated mode the LoginActivity owns login; in standalone dev mode
 * (no token in TokenManager), the agentic screen bootstraps its own session
 * with hardcoded test credentials.
 *
 * Author: Cai Peilin
 */
object AuthApi {

    private const val DEV_USERNAME = "agent_test_user"
    private const val DEV_PASSWORD = "test_password_123"

    @Throws(IOException::class)
    fun loginAsDevUser(): String {
        val conn = openAuthenticatedConnection("/login", "POST", null)
        conn.doOutput = true

        val payload = JSONObject()
            .put("username", DEV_USERNAME)
            .put("password", DEV_PASSWORD)
            .toString()

        conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

        val code = conn.responseCode
        if (code !in 200..299) {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            throw IOException("Login returned $code: $err")
        }
        val body = conn.inputStream.bufferedReader().use { it.readText() }
        return JSONObject(body).getString("accessToken")
    }
}