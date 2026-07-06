package iss.nus.edu.sg.ca_application.auth

// Author: Liu Yu, Wang Songyu

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.SettingsActivity
import iss.nus.edu.sg.ca_application.network.ApiClient
import iss.nus.edu.sg.ca_application.util.onClickDebounced

class RegisterActivity : AppCompatActivity() {

    private lateinit var etUsername: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var tvUsernameError: TextView
    private lateinit var tvEmailError: TextView
    private lateinit var tvPasswordError: TextView

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(SettingsActivity.wrapContextForLocale(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        etUsername = findViewById(R.id.etUsername)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        tvUsernameError = findViewById(R.id.tvUsernameError)
        tvEmailError = findViewById(R.id.tvEmailError)
        tvPasswordError = findViewById(R.id.tvPasswordError)
        // Language toggle
        val btnLangToggle = findViewById<TextView>(R.id.btnLangToggle)
        updateLangButton(btnLangToggle)
        btnLangToggle.setOnClickListener {
            val newLang = if (SettingsActivity.getCurrentLanguage(this) == "zh") "en" else "zh"
            SettingsActivity.saveLocale(this, newLang)
            recreate()
        }

        val btnRegister = findViewById<TextView>(R.id.btnRegister)
        val btnGoLogin = findViewById<TextView>(R.id.btnGoLogin)

        // Clear errors on focus
        etUsername.setOnFocusChangeListener { _, _ -> clearFieldError(etUsername, tvUsernameError) }
        etEmail.setOnFocusChangeListener { _, _ -> clearFieldError(etEmail, tvEmailError) }
        etPassword.setOnFocusChangeListener { _, _ -> clearFieldError(etPassword, tvPasswordError) }

        btnGoLogin.setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        btnRegister.onClickDebounced {
            val username = etUsername.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            clearAllErrors()

            // Validate
            var hasError = false
            if (username.isEmpty()) {
                showFieldError(etUsername, tvUsernameError, getString(R.string.register_error_empty_username))
                hasError = true
            }
            if (email.isNotEmpty() && !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                showFieldError(etEmail, tvEmailError, getString(R.string.invalid_email))
                hasError = true
            }
            if (password.isEmpty() || password.length < 8) {
                showFieldError(etPassword, tvPasswordError, getString(R.string.password_too_weak))
                hasError = true
            } else if (!password.any { it.isDigit() } || !password.any { it.isLetter() }) {
                showFieldError(etPassword, tvPasswordError, getString(R.string.password_need_digit_letter))
                hasError = true
            }
            if (hasError) return@onClickDebounced

            Thread {
                try {
                    val message = ApiClient.register(username, password, email)
                    runOnUiThread {
                        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                        finish()
                    }
                } catch (e: Exception) {
                    runOnUiThread { handleRegisterError(e.message ?: "") }
                }
            }.start()
        }
    }

    private fun handleRegisterError(msg: String) {
        val lower = msg.lowercase()
        when {
            lower.contains("username") && lower.contains("exist") ->
                showFieldError(etUsername, tvUsernameError, getString(R.string.error_username_exists))
            lower.contains("email") && (lower.contains("exist") || lower.contains("registered") || lower.contains("conflict")) ->
                showFieldError(etEmail, tvEmailError, getString(R.string.error_email_exists))
            lower.contains("password") ->
                showFieldError(etPassword, tvPasswordError, msg)
            else ->
                showFieldError(etUsername, tvUsernameError, msg)
        }
    }

    private fun showFieldError(field: EditText, label: TextView, msg: String) {
        field.setBackgroundResource(R.drawable.bg_input_field_error)
        label.text = msg
        label.visibility = TextView.VISIBLE
    }

    private fun clearFieldError(field: EditText, label: TextView) {
        field.setBackgroundResource(R.drawable.bg_input_field)
        label.visibility = TextView.GONE
    }

    private fun updateLangButton(btn: TextView) {
        btn.text = if (SettingsActivity.getCurrentLanguage(this) == "zh") "EN" else "中文"
    }

    private fun clearAllErrors() {
        clearFieldError(etUsername, tvUsernameError)
        clearFieldError(etEmail, tvEmailError)
        clearFieldError(etPassword, tvPasswordError)
    }
}
