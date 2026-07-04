// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.network

import iss.nus.edu.sg.ca_application.model.*
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ProfileApi {

    private const val CONNECT_TIMEOUT_MS = 30_000

    private fun openConn(path: String, method: String, token: String): HttpURLConnection {
        val c = openAuthenticatedConnection(path, method, if (token.isNotEmpty()) token else null)
        c.connectTimeout = CONNECT_TIMEOUT_MS
        return c
    }

    private fun body(c: HttpURLConnection, json: String) {
        c.outputStream.use {
            OutputStreamWriter(it, "UTF-8").apply { write(json); flush() }
        }
    }

    private fun ok(c: HttpURLConnection) = c.responseCode in 200..299

    /** GET /user — combined User + UserProfile info */
    fun getProfile(token: String): UserProfileData {
        val c = openConn("/user", "GET", token)
        if (ok(c)) {
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            c.disconnect()
            return UserProfileData(
                userId = o.getLong("userId"),
                username = o.getString("username"),
                email = o.optString("email", null),
                provider = o.optString("provider", null),
                avatarUrl = o.optString("avatarUrl", null),
                nickname = o.optString("nickname", null),
                heightCm = if (o.has("heightCm") && !o.isNull("heightCm")) o.getInt("heightCm") else null,
                age = if (o.has("age") && !o.isNull("age")) o.getInt("age") else null,
                weightKg = if (o.has("weightKg") && !o.isNull("weightKg")) o.getDouble("weightKg") else null
            )
        }
        val err = c.errorStream?.bufferedReader()?.readText() ?: ""
        c.disconnect()
        throw ApiException(c.responseCode, err)
    }

    /** PUT /profile — partial update of body metrics (height, weight, age) */
    fun updateProfile(token: String, json: JSONObject): JSONObject {
        val c = openConn("/profile", "PUT", token)
        body(c, json.toString())
        if (ok(c)) {
            val resp = JSONObject(c.inputStream.bufferedReader().readText())
            c.disconnect()
            return resp
        }
        val err = c.errorStream?.bufferedReader()?.readText() ?: ""
        c.disconnect()
        throw ApiException(c.responseCode, err)
    }

    /** POST /auth/change-password */
    fun changePassword(token: String, oldPassword: String, newPassword: String): JSONObject {
        val c = openConn("/auth/change-password", "POST", token)
        body(c, JSONObject().apply {
            put("oldPassword", oldPassword)
            put("newPassword", newPassword)
        }.toString())
        if (ok(c)) {
            val resp = JSONObject(c.inputStream.bufferedReader().readText())
            c.disconnect()
            return resp
        }
        val err = c.errorStream?.bufferedReader()?.readText() ?: ""
        c.disconnect()
        throw ApiException(c.responseCode, err)
    }

    /** PUT /auth/email — bind or change email. May return 409 conflict. */
    fun bindEmail(token: String, email: String): JSONObject {
        val c = openConn("/auth/email", "PUT", token)
        body(c, JSONObject().apply { put("email", email) }.toString())
        if (ok(c) || c.responseCode == 409) {
            val stream = if (ok(c)) c.inputStream else c.errorStream
            val resp = JSONObject(stream!!.bufferedReader().readText())
            c.disconnect()
            return resp
        }
        val err = c.errorStream?.bufferedReader()?.readText() ?: ""
        c.disconnect()
        throw ApiException(c.responseCode, err)
    }

    /** PUT /user/username — change username. May return 409 conflict. */
    fun changeUsername(token: String, username: String): JSONObject {
        val c = openConn("/user/username", "PUT", token)
        body(c, JSONObject().apply { put("username", username) }.toString())
        if (ok(c) || c.responseCode == 409) {
            val stream = if (ok(c)) c.inputStream else c.errorStream
            val resp = JSONObject(stream!!.bufferedReader().readText())
            c.disconnect()
            return resp
        }
        val err = c.errorStream?.bufferedReader()?.readText() ?: ""
        c.disconnect()
        throw ApiException(c.responseCode, err)
    }

    /** POST /profile/avatar — multipart file upload. Returns avatarUrl. */
    fun uploadAvatar(token: String, imageBytes: ByteArray, fileName: String): String {
        val url = URL("$BASE_URL/profile/avatar")
        val boundary = "---Boundary${System.currentTimeMillis()}"
        val c = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            doInput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = 30_000
            setRequestProperty("X-API-Token", API_GATEWAY_TOKEN)
            if (token.isNotEmpty()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
        }

        // Build multipart body
        val outputStream = c.outputStream
        val lineEnd = "\r\n"
        val twoHyphens = "--"

        // Determine content type from extension
        val mimeType = if (fileName.endsWith(".png", ignoreCase = true)) "image/png" else "image/jpeg"

        outputStream.write((twoHyphens + boundary + lineEnd).toByteArray())
        outputStream.write(("Content-Disposition: form-data; name=\"file\"; filename=\"$fileName\"" + lineEnd).toByteArray())
        outputStream.write(("Content-Type: $mimeType" + lineEnd).toByteArray())
        outputStream.write(lineEnd.toByteArray())
        outputStream.write(imageBytes)
        outputStream.write(lineEnd.toByteArray())
        outputStream.write((twoHyphens + boundary + twoHyphens + lineEnd).toByteArray())
        outputStream.flush()
        outputStream.close()

        if (c.responseCode in 200..299) {
            val o = JSONObject(c.inputStream.bufferedReader().readText())
            c.disconnect()
            return o.getString("avatarUrl")
        }
        val err = c.errorStream?.bufferedReader()?.readText() ?: ""
        c.disconnect()
        throw ApiException(c.responseCode, err)
    }

    /** DELETE /auth/account — permanently deletes the user account and all data. */
    fun deleteAccount(token: String): JSONObject {
        val c = openConn("/auth/account", "DELETE", token)
        if (ok(c)) {
            val resp = JSONObject(c.inputStream.bufferedReader().readText())
            c.disconnect()
            return resp
        }
        val err = c.errorStream?.bufferedReader()?.readText() ?: ""
        c.disconnect()
        throw ApiException(c.responseCode, err)
    }
}
