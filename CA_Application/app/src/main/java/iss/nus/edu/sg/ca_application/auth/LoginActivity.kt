package iss.nus.edu.sg.ca_application.auth

// Author: Liu Yu, Wang Songyu

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import com.google.android.material.card.MaterialCardView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.common.api.ApiException
import iss.nus.edu.sg.ca_application.MainActivity
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.SettingsActivity
import iss.nus.edu.sg.ca_application.model.LoginResponse
import iss.nus.edu.sg.ca_application.network.ApiClient
import iss.nus.edu.sg.ca_application.util.onClickDebounced
import org.json.JSONObject

class LoginActivity : AppCompatActivity() {

    private lateinit var tvError: TextView
    private var googleSignInClient: GoogleSignInClient? = null

    private val webSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val authCode = result.data?.getStringExtra("authCode")
            val redirectUri = result.data?.getStringExtra("redirectUri") ?: ""
            if (authCode != null) {
                handleGoogleSignInWithAuthCode(authCode, redirectUri)
            } else {
                showSignInError("Google web sign-in failed: no auth code")
            }
        }
    }

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            val account = task.getResult(ApiException::class.java)
            val idToken = account.idToken
            if (idToken != null) {
                handleGoogleSignIn(idToken)
            } else {
                showSignInError(getString(R.string.google_sign_in_no_auth))
            }
        } catch (e: ApiException) {
            if (e.statusCode != 12501) {
                val detail = when (e.statusCode) {
                    10 -> getString(R.string.google_sign_in_developer_error)
                    12500 -> getString(R.string.google_sign_in_retry)
                    else -> "Code ${e.statusCode}"
                }
                showSignInError(getString(R.string.google_sign_in_failed) + ": $detail")
            }
        } catch (e: Exception) {
            showSignInError(getString(R.string.google_sign_in_failed) + ": ${e.message}")
        }
    }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(
            iss.nus.edu.sg.ca_application.SettingsActivity.wrapContextForLocale(newBase)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // requestIdToken with Web client ID — most stable Android config
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("375016829980-l1gpslqj0cltlc5aqf2f403c52oepeg7.apps.googleusercontent.com")
            .requestEmail()
            .requestProfile()
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Auto-login: skip if token already exists
        if (TokenManager.isLoggedIn(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        // Language toggle
        val btnLangToggle = findViewById<TextView>(R.id.btnLangToggle)
        updateLangButton(btnLangToggle)
        btnLangToggle.setOnClickListener {
            val newLang = if (SettingsActivity.getCurrentLanguage(this) == "zh") "en" else "zh"
            SettingsActivity.saveLocale(this, newLang)
            recreate()
        }

        val btnLogin = findViewById<TextView>(R.id.btnLogin)
        val btnRegister = findViewById<TextView>(R.id.btnRegister)
        val btnGoogleSignIn = findViewById<MaterialCardView>(R.id.btnGoogleSignIn)

        tvError = findViewById(R.id.tvError)
        val tvUsernameError = findViewById<TextView>(R.id.tvUsernameError)
        val tvPasswordError = findViewById<TextView>(R.id.tvPasswordError)

        // Clear field errors on focus
        etUsername.setOnFocusChangeListener { _, _ -> clearFieldError(etUsername, tvUsernameError) }
        etPassword.setOnFocusChangeListener { _, _ -> clearFieldError(etPassword, tvPasswordError) }

        btnLogin.onClickDebounced {
            tvError.visibility = View.GONE
            clearFieldError(etUsername, tvUsernameError)
            clearFieldError(etPassword, tvPasswordError)

            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty()) {
                showFieldError(etUsername, tvUsernameError, getString(R.string.login_error_empty))
            } else if (password.isEmpty()) {
                showFieldError(etPassword, tvPasswordError, getString(R.string.login_error_empty))
            } else {
                Thread {
                    try {
                        val response: LoginResponse = ApiClient.login(username, password)

                        runOnUiThread {
                            tvError.visibility = View.GONE

                            TokenManager.saveToken(this@LoginActivity, response.accessToken, response.tokenType)
                            TokenManager.saveUsername(this@LoginActivity, response.username)
                            TokenManager.saveProvider(this@LoginActivity, "LOCAL")

                            Toast.makeText(this@LoginActivity, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
                            startActivity(Intent(this@LoginActivity, MainActivity::class.java))
                            finish()
                        }
                    } catch (e: Exception) {
                        runOnUiThread {
                            val message = e.message ?: ""
                            when {
                                message.contains("Bad credentials", ignoreCase = true) ||
                                message.contains("Incorrect", ignoreCase = true) -> {
                                    showFieldError(etUsername, tvUsernameError, "")
                                    showFieldError(etPassword, tvPasswordError, getString(R.string.login_error_credentials))
                                }
                                message.contains("User not found", ignoreCase = true) ->
                                    showFieldError(etUsername, tvUsernameError, getString(R.string.login_error_user_not_found))
                                else -> {
                                    tvError.text = getString(R.string.login_error_generic)
                                    tvError.visibility = View.VISIBLE
                                }
                            }
                        }
                    }
                }.start()
            }
        }

        btnRegister.onClickDebounced {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        btnGoogleSignIn.onClickDebounced {
            tvError.visibility = View.GONE
            val gpsStatus = GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(this)
            if (gpsStatus != ConnectionResult.SUCCESS) {
                webSignInLauncher.launch(Intent(this, GoogleWebSignInActivity::class.java))
            } else {
                // Clear cached Google state before launching sign-in
                googleSignInClient?.signOut()?.addOnCompleteListener {
                    val intent = googleSignInClient?.signInIntent
                    if (intent != null) googleSignInLauncher.launch(intent)
                    else {
                        tvError.text = getString(R.string.google_sign_in_not_available)
                        tvError.visibility = View.VISIBLE
                    }
                }
            }
        }
    }

    private fun handleGoogleSignIn(idToken: String) {
        Thread {
            try {
                val response = ApiClient.googleLogin(idToken, "")
                runOnUiThread { handleGoogleResponse(idToken, "", response) }
            } catch (e: Exception) {
                runOnUiThread {
                    val detail = when (e) {
                        is iss.nus.edu.sg.ca_application.network.ApiException -> "[HTTP ${e.code}] ${e.message}"
                        else -> "[${e.javaClass.simpleName}] ${e.message}"
                    }
                    tvError.text = "Google sign-in failed\n$detail"
                    tvError.visibility = View.VISIBLE
                }
            }
        }.start()
    }

    /** Handle Google sign-in via Chrome Custom Tabs (devices without Google Play Services). */
    private fun handleGoogleSignInWithAuthCode(authCode: String, redirectUri: String) {
        Thread {
            try {
                val response = ApiClient.googleLogin("", "", authCode, redirectUri)
                runOnUiThread { handleGoogleResponse("", authCode, response) }
            } catch (e: Exception) {
                runOnUiThread {
                    showSignInError(getString(R.string.google_sign_in_failed) + ": ${e.message}")
                }
            }
        }.start()
    }

    /** Dispatch Google login response: conflict / new user / success. */
    private fun handleGoogleResponse(idToken: String, authCode: String, response: JSONObject) {
        if (response.optBoolean("conflict", false)) {
            showGoogleConflictDialog(idToken, authCode,
                response.optString("existingUsername", ""),
                response.optString("email", ""))
        } else if (response.optBoolean("newUser", false)) {
            showGoogleNewUserDialog(idToken, authCode,
                response.optString("email", ""),
                response.optString("suggestedUsername", ""))
        } else {
            finishGoogleLogin(response)
        }
    }

    /** Custom dialog for existing-account conflict. */
    private fun showGoogleConflictDialog(idToken: String, authCode: String, existingUsername: String, email: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_google_confirm, null)
        view.findViewById<TextView>(R.id.tvDialogEmail).text = email
        view.findViewById<TextView>(R.id.tvDialogMessage).text =
            getString(R.string.google_conflict_message, existingUsername, existingUsername)
        view.findViewById<View>(R.id.layoutUsernameInput).visibility = View.GONE
        view.findViewById<TextView>(R.id.btnDialogConfirm).text =
            getString(R.string.google_conflict_yes, existingUsername)

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        view.findViewById<TextView>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnDialogConfirm).setOnClickListener {
            dialog.dismiss()
            Thread {
                try {
                    val resp = if (authCode.isNotEmpty()) ApiClient.googleLogin("", existingUsername, authCode)
                               else ApiClient.googleLogin(idToken, existingUsername)
                    runOnUiThread { finishGoogleLogin(resp) }
                } catch (e: Exception) {
                    runOnUiThread { tvError.text = "Failed: ${e.message}"; tvError.visibility = View.VISIBLE }
                }
            }.start()
        }
        dialog.show()
    }

    /** Custom dialog for new-user confirmation. */
    private fun showGoogleNewUserDialog(idToken: String, authCode: String, email: String, suggestedUsername: String) {
        val view = LayoutInflater.from(this).inflate(R.layout.dialog_google_confirm, null)
        view.findViewById<TextView>(R.id.tvDialogEmail).text = email
        view.findViewById<TextView>(R.id.tvDialogMessage).text = getString(R.string.google_new_user_message)
        view.findViewById<View>(R.id.layoutUsernameInput).visibility = View.VISIBLE
        val etUsername = view.findViewById<EditText>(R.id.etDialogUsername)
        val tvUsernameError = view.findViewById<TextView>(R.id.tvDialogUsernameError)
        etUsername.setText(suggestedUsername)
        view.findViewById<TextView>(R.id.btnDialogConfirm).text = getString(R.string.google_new_user_confirm)

        // Clear error on focus
        etUsername.setOnFocusChangeListener { _, _ ->
            etUsername.setBackgroundResource(R.drawable.bg_input_field)
            tvUsernameError.visibility = View.GONE
        }

        val dialog = AlertDialog.Builder(this).setView(view).setCancelable(true).create()
        view.findViewById<TextView>(R.id.btnDialogCancel).setOnClickListener { dialog.dismiss() }
        view.findViewById<TextView>(R.id.btnDialogConfirm).setOnClickListener {
            val chosen = etUsername.text.toString().trim()
            if (chosen.isEmpty() || chosen.length < 3) {
                Toast.makeText(this, R.string.login_error_empty, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            view.findViewById<TextView>(R.id.btnDialogConfirm).isEnabled = false
            Thread {
                try {
                    val resp = if (authCode.isNotEmpty()) ApiClient.googleLogin("", chosen, authCode)
                               else ApiClient.googleLogin(idToken, chosen)
                    runOnUiThread {
                        if (resp.optBoolean("newUser", false) && "username_taken" == resp.optString("error", "")) {
                            // Username taken — show error inline, keep dialog open
                            etUsername.setBackgroundResource(R.drawable.bg_input_field_error)
                            tvUsernameError.text = getString(R.string.error_username_exists)
                            tvUsernameError.visibility = View.VISIBLE
                            etUsername.setText(resp.optString("suggestedUsername", chosen))
                            view.findViewById<TextView>(R.id.btnDialogConfirm).isEnabled = true
                        } else {
                            dialog.dismiss()
                            finishGoogleLogin(resp)
                        }
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        view.findViewById<TextView>(R.id.btnDialogConfirm).isEnabled = true
                        tvError.text = "Failed: ${e.message}"; tvError.visibility = View.VISIBLE
                    }
                }
            }.start()
        }
        dialog.show()
    }

    private fun showSignInError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
    }

    private fun updateLangButton(btn: TextView) {
        btn.text = if (SettingsActivity.getCurrentLanguage(this) == "zh") "EN" else "中文"
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

    /** Complete Google login with the successful response. */
    private fun finishGoogleLogin(response: JSONObject) {
        TokenManager.saveToken(this, response.getString("accessToken"), response.optString("tokenType", "bearer"))
        TokenManager.saveUsername(this, response.getString("username"))
        TokenManager.saveProvider(this, "GOOGLE")
        Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
