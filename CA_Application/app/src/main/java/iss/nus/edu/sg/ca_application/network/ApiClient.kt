package iss.nus.edu.sg.ca_application.network

import iss.nus.edu.sg.ca_application.model.LoginRequest
import iss.nus.edu.sg.ca_application.model.LoginResponse
import iss.nus.edu.sg.ca_application.model.WellnessEntry
import iss.nus.edu.sg.ca_application.model.WellnessRecord
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Author: Wang Songyu, Liu Yu
 *
 * Centralized HTTP client for all backend API calls.
 *
 * Server:
 *   Ubuntu 24.04 running FastAPI on port 8000
 *
 * Required headers on EVERY authenticated request:
 *   Authorization: Bearer <jwt_access_token>
 *   X-API-Token:   team-wellness-2025
 *   Content-Type:  application/json
 *
 * The API gateway token (team-wellness-2025) is a shared team secret.
 * It is NOT the DeepSeek API key — that never leaves the server.
 *
 * Endpoints consumed by the Android app:
 *   POST   /login              — authenticate and receive JWT
 *   POST   /register           — create new user account
 *   GET    /records            — list user's wellness records
 *   POST   /records            — create a new wellness record
 *   PUT    /records/{id}       — update an existing record
 *   DELETE /records/{id}       — delete a record
 *   POST   /chat               — send a message to the AI chatbot
 *   GET    /recommendations    — retrieve AI-generated recommendations
 *
 * Error handling:
 *   HTTP 401 → JWT expired or invalid → redirect to LoginActivity
 *   HTTP 403 → API gateway token rejected
 *   HTTP 4xx/5xx → show user-friendly Toast/Dialog
 *
 * Threading:
 *   All network calls MUST run on a background thread (Thread { }).
 *   UI updates MUST use runOnUiThread { }.
 *
 * Dependencies:
 *   java.net.HttpURLConnection (as taught in 07_Image_Download.pdf)
 *   org.json.JSONObject (for JSON parsing)
 */

const val BASE_URL = "http://152.42.181.66:8000"
const val API_GATEWAY_TOKEN = "team-wellness-2025"

object ApiClient {
    private fun createConnection(
        endpoint: String,
        method: String,
        token: String
    ): HttpURLConnection {

        val url = URL(BASE_URL + endpoint)
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = method
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("X-API-Token", API_GATEWAY_TOKEN)

        if (token.isNotEmpty()) {
            connection.setRequestProperty(
                "Authorization",
                "Bearer $token"
            )
        }

        connection.doInput = true

        if (method == "POST" || method == "PUT") {
            connection.doOutput = true
        }

        return connection
    }
    /**
     * Retrieves all wellness records belonging to the current user.
     *
     * Endpoint:
     * GET /records
     *
     * This method must be called on a background thread.
     *
     * @param token JWT access token
     * @return List of wellness records
     * @throws ApiException if the server returns an error
     */
    fun getRecords(token: String): List<WellnessRecord> {
        val records = mutableListOf<WellnessRecord>()
        var connection: HttpURLConnection? = null

        try {
            connection = createConnection("/records", "GET", token)
            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val jsonArray = JSONArray(response)

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    records.add(
                        WellnessRecord(
                            id = obj.getInt("id"),
                            sleepHours = obj.getDouble("sleepHours"),
                            exerciseActivity = obj.getString("exerciseActivity"),
                            exerciseDuration = obj.getInt("exerciseDuration"),
                            recordDate = obj.getString("recordDate"),
                            notes = obj.optString("notes", "")
                        )
                    )
                }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "No error details"
                throw ApiException(responseCode, errorBody)
            }
        } finally {
            connection?.disconnect()
        }

        return records
    }

    /**
     * Creates a new wellness record.
     *
     * Endpoint:
     * POST /records
     *
     * The backend returns only the generated record ID.
     * A complete WellnessRecord object is reconstructed locally
     * using the returned ID and the submitted request body.
     *
     * This method must be called on a background thread.
     *
     * @param token JWT access token
     * @param entry Wellness record to create
     * @return Newly created wellness record
     * @throws ApiException if the server returns an error
     */
    fun createRecord(token: String, entry: WellnessEntry): WellnessRecord {
        var connection: HttpURLConnection? = null

        try {
            connection = createConnection("/records", "POST", token)

            val jsonBody = JSONObject().apply {
                put("sleepHours", entry.sleepHours)
                put("exerciseActivity", entry.exerciseActivity)
                put("exerciseDuration", entry.exerciseDuration)
                put("recordDate", entry.recordDate)
                put("notes", entry.notes)
            }

            connection.outputStream.use { os ->
                val writer = OutputStreamWriter(os, "UTF-8")
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(response)
                val newId = obj.getInt("id")

                // Backend returns only id; reconstruct locally
                return WellnessRecord(
                    id = newId,
                    sleepHours = entry.sleepHours,
                    exerciseActivity = entry.exerciseActivity,
                    exerciseDuration = entry.exerciseDuration,
                    recordDate = entry.recordDate,
                    notes = entry.notes
                )
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "No error details"
                throw ApiException(responseCode, errorBody)
            }
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Updates an existing wellness record.
     *
     * Endpoint:
     * PUT /records/{id}
     *
     * The backend returns only a success message.
     * A new WellnessRecord object is reconstructed locally
     * using the updated data.
     *
     * This method must be called on a background thread.
     *
     * @param token JWT access token
     * @param id Record ID
     * @param entry Updated wellness data
     * @return Updated wellness record
     * @throws ApiException if the server returns an error
     */
    fun updateRecord(token: String, id: Int, entry: WellnessEntry): WellnessRecord {
        var connection: HttpURLConnection? = null

        try {
            connection = createConnection("/records/$id", "PUT", token)

            val jsonBody = JSONObject().apply {
                put("sleepHours", entry.sleepHours)
                put("exerciseActivity", entry.exerciseActivity)
                put("exerciseDuration", entry.exerciseDuration)
                put("recordDate", entry.recordDate)
                put("notes", entry.notes)
            }

            connection.outputStream.use { os ->
                val writer = OutputStreamWriter(os, "UTF-8")
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Backend returns only message; reconstruct locally
                return WellnessRecord(
                    id = id,
                    sleepHours = entry.sleepHours,
                    exerciseActivity = entry.exerciseActivity,
                    exerciseDuration = entry.exerciseDuration,
                    recordDate = entry.recordDate,
                    notes = entry.notes
                )
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "No error details"
                throw ApiException(responseCode, errorBody)
            }
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Deletes a wellness record.
     *
     * Endpoint:
     * DELETE /records/{id}
     *
     * The backend returns no response body on success.
     *
     * This method must be called on a background thread.
     *
     * @param token JWT access token
     * @param id Record ID
     * @throws ApiException if the server returns an error
     */
    fun deleteRecord(token: String, id: Int) {
        var connection: HttpURLConnection? = null

        try {
            connection = createConnection("/records/$id", "DELETE", token)
            val responseCode = connection.responseCode

            if (responseCode != HttpURLConnection.HTTP_OK &&
                responseCode != HttpURLConnection.HTTP_NO_CONTENT) {

                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "No error details"
                throw ApiException(responseCode, errorBody)
            }
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Authenticates a user with the backend.
     *
     * Endpoint:
     * POST /login
     *
     * Request body: { "username": "...", "password": "..." }
     * Response (200): { "accessToken": "...", "tokenType": "bearer", "userId": 1, "username": "..." }
     *
     * This method must be called on a background thread.
     *
     * @param username Username
     * @param password Password
     * @return LoginResponse with JWT and user info
     * @throws ApiException if credentials are invalid or server returns an error
     */
    fun login(username: String, password: String): LoginResponse {
        var connection: HttpURLConnection? = null

        try {
            connection = createConnection("/login", "POST", "")

            val jsonBody = JSONObject().apply {
                put("username", username)
                put("password", password)
            }

            connection.outputStream.use { os ->
                val writer = OutputStreamWriter(os, "UTF-8")
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(response)
                return LoginResponse(
                    accessToken = obj.getString("accessToken"),
                    tokenType = obj.getString("tokenType"),
                    userId = obj.optLong("userId", -1),
                    username = obj.optString("username", "")
                )
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "No error details"
                throw ApiException(responseCode, errorBody)
            }
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Registers a new user account.
     *
     * Endpoint:
     * POST /register
     *
     * Request body: { "username": "...", "password": "..." }
     * Response (200/201): { "message": "User registered successfully" }
     *
     * This method must be called on a background thread.
     *
     * @param username Desired username
     * @param password Desired password
     * @return Success message from the server
     * @throws ApiException if registration fails (e.g. username taken)
     */
    fun register(username: String, password: String): String {
        var connection: HttpURLConnection? = null

        try {
            connection = createConnection("/register", "POST", "")

            val jsonBody = JSONObject().apply {
                put("username", username)
                put("password", password)
            }

            connection.outputStream.use { os ->
                val writer = OutputStreamWriter(os, "UTF-8")
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK ||
                responseCode == HttpURLConnection.HTTP_CREATED) {

                return if (responseCode == HttpURLConnection.HTTP_CREATED) {
                    "Register Successful"
                } else {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    try {
                        JSONObject(response).optString("message", "Register Successful")
                    } catch (e: Exception) {
                        "Register Successful"
                    }
                }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() }
                    ?: "No error details"
                throw ApiException(responseCode, errorBody)
            }
        } finally {
            connection?.disconnect()
        }
    }
}


fun openAuthenticatedConnection(
    path: String,
    method: String,
    bearerToken: String? = null
): HttpURLConnection {
    val url = URL("$BASE_URL$path")
    return (url.openConnection() as HttpURLConnection).apply {
        requestMethod = method
        setRequestProperty("X-API-Token", API_GATEWAY_TOKEN)
        setRequestProperty("Content-Type", "application/json")
        if (bearerToken != null) {
            setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        connectTimeout = 10_000
        // Agent calls can take 5-15 seconds; allow up to 30 to be safe.
        readTimeout = 30_000
    }
}