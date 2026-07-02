package iss.nus.edu.sg.ca_application.auth

// Author: Liu Yu, Wang Songyu

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.ca_application.MainActivity
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.model.LoginResponse
import iss.nus.edu.sg.ca_application.network.ApiClient

/**
 * Login screen — first Activity the user sees.
 *
 * Flow:
 *   1. If already logged in (token exists) → skip to MainActivity
 *   2. User enters username and password
 *   3. App sends POST /login with JSON body { "username": "...", "password": "..." }
 *   4. On success (200): store JWT via TokenManager → navigate to MainActivity
 *   5. On failure (400): show error in tvError TextView
 *   6. Register button → navigate to RegisterActivity
 *
 * Layout: res/layout/activity_login.xml
 *
 * Dependencies:
 *   network.ApiClient       — for HTTP calls
 *   auth.TokenManager       — for JWT persistence
 *   model.LoginResponse     — response body data class
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // Auto-login: skip if token already exists
        if (TokenManager.isLoggedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        tvError = findViewById(R.id.tvError)

        btnLogin.setOnClickListener {
            tvError.visibility = View.GONE

            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                tvError.text = getString(R.string.login_error_empty)
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            Thread {
                try {
                    val response: LoginResponse = ApiClient.login(username, password)

                    runOnUiThread {
                        tvError.visibility = View.GONE

                        TokenManager.saveToken(
                            this@LoginActivity,
                            response.accessToken,
                            response.tokenType
                        )

                        Toast.makeText(
                            this@LoginActivity,
                            getString(R.string.login_success),
                            Toast.LENGTH_SHORT
                        ).show()

                        startActivity(
                            Intent(this@LoginActivity, MainActivity::class.java)
                        )
                        finish()
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        val message = e.message ?: ""
                        val friendlyMessage = when {
                            message.contains("Bad credentials", ignoreCase = true) ->
                                getString(R.string.login_error_credentials)
                            message.contains("Incorrect username or password", ignoreCase = true) ->
                                getString(R.string.login_error_credentials)
                            message.contains("User not found", ignoreCase = true) ->
                                getString(R.string.login_error_user_not_found)
                            else ->
                                getString(R.string.login_error_generic)
                        }
                        tvError.text = friendlyMessage
                        tvError.visibility = View.VISIBLE
                    }
                }
            }.start()
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}
