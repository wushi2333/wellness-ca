package iss.nus.edu.sg.ca_application.network

import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.gson.Gson
import iss.nus.edu.sg.ca_application.model.LoginRequest
import iss.nus.edu.sg.ca_application.model.LoginResponse
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException

object ApiClient {

    const val BASE_URL = "http://10.0.2.2:8000"
    const val API_TOKEN = "team-wellness-2025"

    private val JSON = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient()
    private val handler = Handler(Looper.getMainLooper())
    private val gson = Gson()

    interface LoginCallback {
        fun onSuccess(response: LoginResponse)
        fun onFailure(message: String)
    }

    interface RegisterCallback {
        fun onSuccess(message: String)
        fun onFailure(message: String)
    }

    // ================= LOGIN =================
    fun login(
        username: String,
        password: String,
        callback: LoginCallback
    ) {

        val requestObject = LoginRequest(username, password)
        val json = gson.toJson(requestObject)
        val body = json.toRequestBody(JSON)

        val request = Request.Builder()
            .url("$BASE_URL/login")
            .addHeader("X-API-Token", API_TOKEN)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    callback.onFailure(e.message ?: "Network Error")
                }
            }

            override fun onResponse(call: Call, response: Response) {

                val resultString = response.body?.string()

                if (response.isSuccessful && resultString != null) {

                    val loginResponse =
                        gson.fromJson(resultString, LoginResponse::class.java)

                    handler.post {
                        callback.onSuccess(loginResponse)
                    }

                } else {

                    val message = try {
                        val error = resultString ?: "Login Failed"
                        JSONObject(error).getString("detail")
                    } catch (e: Exception) {
                        "Login Failed"
                    }

                    handler.post {
                        callback.onFailure(message)
                    }
                }
            }
        })
    }

    // ================= REGISTER =================
    fun register(
        username: String,
        password: String,
        callback: RegisterCallback
    ) {

        val requestObject = LoginRequest(username, password)
        val json = gson.toJson(requestObject)
        val body = json.toRequestBody(JSON)

        val request = Request.Builder()
            .url("$BASE_URL/register")
            .addHeader("X-API-Token", API_TOKEN)
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        client.newCall(request).enqueue(object : Callback {

            override fun onFailure(call: Call, e: IOException) {
                handler.post {
                    callback.onFailure(e.message ?: "Network Error")
                }
            }

            override fun onResponse(call: Call, response: Response) {

                val resultString = response.body?.string()

                if (response.isSuccessful) {

                    handler.post {
                        callback.onSuccess("Register Successful")
                    }

                } else {

                    val message = try {
                        val error = resultString ?: "Register Failed"
                        JSONObject(error).getString("detail")
                    } catch (e: Exception) {
                        "Register Failed"
                    }

                    handler.post {
                        callback.onFailure(message)
                    }
                }
            }
        })
    }
}