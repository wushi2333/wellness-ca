package iss.nus.edu.sg.ca_application.network

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
 * HTTP client for Spring Boot backend at port 8000.
 * All requests carry Authorization: Bearer and X-API-Token headers.
 * All network calls must run on a background thread.
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
}