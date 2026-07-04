package iss.nus.edu.sg.ca_application.auth

// Author: Liu Yu, Wang Songyu

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.SettingsActivity
import iss.nus.edu.sg.ca_application.network.ApiClient

/**
 * Register screen — allows new users to create an account.
 *
 * Flow:
 *   1. User enters username and password
 *   2. App sends POST /register with JSON body { "username": "...", "password": "..." }
 *   3. On success: Toast → finish() back to LoginActivity
 *   4. On failure: show error Toast
 *
 * Layout: res/layout/activity_register.xml
 */
class RegisterActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SettingsActivity.wrapContextForLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etEmail = findViewById<EditText>(R.id.etEmail)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnRegister.setOnClickListener {
            val username = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                Toast.makeText(
                    this,
                    getString(R.string.register_error_empty),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                Toast.makeText(
                    this,
                    getString(R.string.invalid_email),
                    Toast.LENGTH_SHORT
                ).show()
                return@setOnClickListener
            }

            Thread {
                try {
                    val message = ApiClient.register(username, password, email)

                    runOnUiThread {
                        Toast.makeText(
                            this,
                            message,
                            Toast.LENGTH_SHORT
                        ).show()
                        finish() // back to LoginActivity
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            e.message ?: getString(R.string.login_error_generic),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }
            }.start()
        }
    }
}
