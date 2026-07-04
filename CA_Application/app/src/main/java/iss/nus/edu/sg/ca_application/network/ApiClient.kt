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
 *   Ubuntu 24.04 running Spring Boot on port 8000
 *
 * Required headers on EVERY authenticated request:
 *   Authorization: Bearer <jwtAccessToken>
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

private const val CONNECT_TIMEOUT_MS = 10_000
private const val READ_TIMEOUT_MS = 10_000

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
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = READ_TIMEOUT_MS

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
    fun getRecords(token: String, page: Int = 0, size: Int = 20): Pair<List<WellnessRecord>, Boolean> {
        val records = mutableListOf<WellnessRecord>()
        var hasMore = false
        var connection: HttpURLConnection? = null

        try {
            connection = createConnection("/records?page=$page&size=$size", "GET", token)
            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK) {
                val reader = BufferedReader(InputStreamReader(connection.inputStream))
                val response = reader.readText()
                reader.close()

                val obj = JSONObject(response)
                val contentArray = obj.getJSONArray("content")
                hasMore = !obj.getBoolean("last")

                for (i in 0 until contentArray.length()) {
                    val item = contentArray.getJSONObject(i)
                    records.add(
                        WellnessRecord(
                            id = item.getInt("id"),
                            sleepHours = item.getDouble("sleepHours"),
                            sleepTime = item.optString("sleepTime", ""),
                            wakeTime = item.optString("wakeTime", ""),
                            exerciseActivity = item.getString("exerciseActivity"),
                            exerciseDuration = item.getInt("exerciseDuration"),
                            moodScore = item.optInt("moodScore", 0),
                            recordDate = item.getString("recordDate"),
                            notes = item.optString("notes", "")
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

        return Pair(records, hasMore)
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
                if (entry.sleepTime.isNotEmpty()) put("sleepTime", entry.sleepTime)
                if (entry.wakeTime.isNotEmpty()) put("wakeTime", entry.wakeTime)
                put("exerciseActivity", entry.exerciseActivity)
                put("exerciseDuration", entry.exerciseDuration)
                if (entry.moodScore > 0) put("moodScore", entry.moodScore)
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
                    sleepTime = entry.sleepTime,
                    wakeTime = entry.wakeTime,
                    exerciseActivity = entry.exerciseActivity,
                    exerciseDuration = entry.exerciseDuration,
                    moodScore = entry.moodScore,
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

    // ── New split-model API (sleep / exercise / daily) ───────────────────

    /**
     * Fetches paginated daily wellness data from GET /records.
     * Each DailyWellness aggregates one date with optional sleep + exercise list.
     */
    fun getDailyRecords(token: String, page: Int = 0, size: Int = 20): Pair<List<iss.nus.edu.sg.ca_application.model.DailyWellness>, Boolean> {
        var connection: HttpURLConnection? = null
        val records = mutableListOf<iss.nus.edu.sg.ca_application.model.DailyWellness>()
        var hasMore = false
        try {
            connection = createConnection("/records?page=$page&size=$size", "GET", token)
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                hasMore = !json.optBoolean("last", true)
                val content = json.getJSONArray("content")
                for (i in 0 until content.length()) {
                    val d = content.getJSONObject(i)
                    val dailyId = d.getLong("dailyRecordId")
                    val date = d.getString("recordDate")

                    // Parse sleep (nullable)
                    var sleep: iss.nus.edu.sg.ca_application.model.SleepRecord? = null
                    if (!d.isNull("sleep")) {
                        val s = d.getJSONObject("sleep")
                        sleep = iss.nus.edu.sg.ca_application.model.SleepRecord(
                            id = s.getLong("id"),
                            sleepHours = s.getDouble("sleepHours"),
                            sleepTime = s.optString("sleepTime", ""),
                            wakeTime = s.optString("wakeTime", ""),
                            moodScore = s.optInt("moodScore", 0),
                            notes = s.optString("notes", "")
                        )
                    }

                    // Parse exercises
                    val exList = mutableListOf<iss.nus.edu.sg.ca_application.model.ExerciseRecord>()
                    val exArr = d.optJSONArray("exercises")
                    if (exArr != null) {
                        for (j in 0 until exArr.length()) {
                            val e = exArr.getJSONObject(j)
                            exList.add(iss.nus.edu.sg.ca_application.model.ExerciseRecord(
                                id = e.getLong("id"),
                                exerciseActivity = e.getString("exerciseActivity"),
                                exerciseDuration = e.getInt("exerciseDuration"),
                                notes = e.optString("notes", "")
                            ))
                        }
                    }
                    records.add(iss.nus.edu.sg.ca_application.model.DailyWellness(
                        dailyRecordId = dailyId, recordDate = date,
                        sleep = sleep, exercises = exList
                    ))
                }
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                throw ApiException(responseCode, errorBody)
            }
        } finally {
            connection?.disconnect()
        }
        return Pair(records, hasMore)
    }

    /** Creates a sleep record. Returns the sleep record ID. */
    fun createSleepRecord(token: String, sleepHours: Double, sleepTime: String, wakeTime: String,
                          moodScore: Int, recordDate: String, notes: String): Long {
        var connection: HttpURLConnection? = null
        try {
            connection = createConnection("/sleep-records", "POST", token)
            val jsonBody = JSONObject().apply {
                put("sleepHours", sleepHours)
                if (sleepTime.isNotEmpty()) put("sleepTime", sleepTime)
                if (wakeTime.isNotEmpty()) put("wakeTime", wakeTime)
                if (moodScore > 0) put("moodScore", moodScore)
                put("recordDate", recordDate)
                put("notes", notes)
            }
            connection.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { w -> w.write(jsonBody.toString()); w.flush() }
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_CREATED) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                return JSONObject(body).getLong("id")
            }
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
            throw ApiException(code, errorBody)
        } finally {
            connection?.disconnect()
        }
    }

    /** Creates an exercise record. Returns the exercise record ID. */
    fun createExerciseRecord(token: String, exerciseActivity: String, exerciseDuration: Int,
                             recordDate: String, notes: String): Long {
        var connection: HttpURLConnection? = null
        try {
            connection = createConnection("/exercise-records", "POST", token)
            val jsonBody = JSONObject().apply {
                put("exerciseActivity", exerciseActivity)
                put("exerciseDuration", exerciseDuration)
                put("recordDate", recordDate)
                put("notes", notes)
            }
            connection.outputStream.use { os ->
                OutputStreamWriter(os, "UTF-8").use { w -> w.write(jsonBody.toString()); w.flush() }
            }
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_CREATED) {
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                return JSONObject(body).getLong("id")
            }
            val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
            throw ApiException(code, errorBody)
        } finally {
            connection?.disconnect()
        }
    }

    /** Update a sleep record. */
    fun updateSleepRecord(token: String, id: Long, sleepHours: Double, sleepTime: String,
                          wakeTime: String, moodScore: Int, recordDate: String, notes: String) {
        var c: HttpURLConnection? = null
        try {
            c = createConnection("/sleep-records/$id", "PUT", token)
            val body = JSONObject().apply {
                put("sleepHours", sleepHours)
                if (sleepTime.isNotEmpty()) put("sleepTime", sleepTime)
                if (wakeTime.isNotEmpty()) put("wakeTime", wakeTime)
                if (moodScore > 0) put("moodScore", moodScore)
                if (recordDate.isNotEmpty()) put("recordDate", recordDate)
                put("notes", notes)
            }
            c.outputStream.use { os -> OutputStreamWriter(os, "UTF-8").use { w -> w.write(body.toString()); w.flush() } }
            if (c.responseCode !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                throw ApiException(c.responseCode, err)
            }
        } finally { c?.disconnect() }
    }

    /** Delete a sleep record. */
    fun deleteSleepRecord(token: String, id: Long) {
        var c: HttpURLConnection? = null
        try {
            c = createConnection("/sleep-records/$id", "DELETE", token)
            if (c.responseCode !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                throw ApiException(c.responseCode, err)
            }
        } finally { c?.disconnect() }
    }

    /** Update an exercise record. */
    fun updateExerciseRecord(token: String, id: Long, exerciseActivity: String,
                             exerciseDuration: Int, recordDate: String, notes: String) {
        var c: HttpURLConnection? = null
        try {
            c = createConnection("/exercise-records/$id", "PUT", token)
            val body = JSONObject().apply {
                put("exerciseActivity", exerciseActivity)
                put("exerciseDuration", exerciseDuration)
                if (recordDate.isNotEmpty()) put("recordDate", recordDate)
                put("notes", notes)
            }
            c.outputStream.use { os -> OutputStreamWriter(os, "UTF-8").use { w -> w.write(body.toString()); w.flush() } }
            if (c.responseCode !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                throw ApiException(c.responseCode, err)
            }
        } finally { c?.disconnect() }
    }

    /** Delete an exercise record. */
    fun deleteExerciseRecord(token: String, id: Long) {
        var c: HttpURLConnection? = null
        try {
            c = createConnection("/exercise-records/$id", "DELETE", token)
            if (c.responseCode !in 200..299) {
                val err = c.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
                throw ApiException(c.responseCode, err)
            }
        } finally { c?.disconnect() }
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
                if (entry.sleepTime.isNotEmpty()) put("sleepTime", entry.sleepTime)
                if (entry.wakeTime.isNotEmpty()) put("wakeTime", entry.wakeTime)
                put("exerciseActivity", entry.exerciseActivity)
                put("exerciseDuration", entry.exerciseDuration)
                if (entry.moodScore > 0) put("moodScore", entry.moodScore)
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
                    sleepTime = entry.sleepTime,
                    wakeTime = entry.wakeTime,
                    exerciseActivity = entry.exerciseActivity,
                    exerciseDuration = entry.exerciseDuration,
                    moodScore = entry.moodScore,
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
    fun register(username: String, password: String, email: String = ""): String {
        var connection: HttpURLConnection? = null

        try {
            connection = createConnection("/register", "POST", "")

            val jsonBody = JSONObject().apply {
                put("username", username)
                put("password", password)
                if (email.isNotEmpty()) put("email", email)
            }

            connection.outputStream.use { os ->
                val writer = OutputStreamWriter(os, "UTF-8")
                writer.write(jsonBody.toString())
                writer.flush()
            }

            val responseCode = connection.responseCode

            if (responseCode == HttpURLConnection.HTTP_OK ||
                responseCode == HttpURLConnection.HTTP_CREATED) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                try {
                    return JSONObject(response).optString("message", response)
                } catch (e: Exception) {
                    return response.ifEmpty { "OK" }
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

    /**
     * Google Sign-In: sends ID token + optional username to backend.
     * Returns the JSON response (may contain conflict, accessToken, etc.).
     */
    fun googleLogin(idToken: String, username: String, authCode: String = "", redirectUri: String = ""): JSONObject {
        var connection: HttpURLConnection? = null
        try {
            connection = createConnection("/auth/google", "POST", "")
            val jsonBody = JSONObject().apply {
                if (idToken.isNotEmpty()) put("idToken", idToken)
                if (authCode.isNotEmpty()) put("authCode", authCode)
                if (redirectUri.isNotEmpty()) put("redirectUri", redirectUri)
                put("username", username)
            }
            connection.outputStream.use { os ->
                val writer = OutputStreamWriter(os, "UTF-8")
                writer.write(jsonBody.toString())
                writer.flush()
            }
            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK || responseCode == 409) {
                return JSONObject(connection.inputStream.bufferedReader().use { it.readText() })
            } else {
                val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: "No error details"
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
        connectTimeout = CONNECT_TIMEOUT_MS
        // Agent calls can take 5-15 seconds; allow up to 30 to be safe.
        readTimeout = 30_000
    }
}