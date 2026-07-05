package iss.nus.edu.sg.ca_application.auth

// Author: Liu Yu, Wang Songyu

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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
                showSignInError("Google sign-in failed: no auth code")
            }
        } catch (e: ApiException) {
            if (e.statusCode != 12501) {
                val detail = when (e.statusCode) {
                    10 -> "Developer error — check SHA-1 fingerprint and Google Cloud Console config"
                    12500 -> "Sign-in failed — try again"
                    else -> "Code ${e.statusCode}"
                }
                showSignInError("Google sign-in failed: $detail")
            }
        } catch (e: Exception) {
            showSignInError("Google sign-in failed: ${e.message}")
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
        val btnLogin = findViewById<Button>(R.id.btnLogin)
        val btnRegister = findViewById<Button>(R.id.btnRegister)
        val btnGoogleSignIn = findViewById<Button>(R.id.btnGoogleSignIn)

        tvError = findViewById(R.id.tvError)

        btnLogin.onClickDebounced {
            tvError.visibility = View.GONE

            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                tvError.text = getString(R.string.login_error_empty)
                tvError.visibility = View.VISIBLE
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
                            val friendlyMessage = when {
                                message.contains("Bad credentials", ignoreCase = true) -> getString(R.string.login_error_credentials)
                                message.contains("Incorrect username or password", ignoreCase = true) -> getString(R.string.login_error_credentials)
                                message.contains("User not found", ignoreCase = true) -> getString(R.string.login_error_user_not_found)
                                else -> getString(R.string.login_error_generic)
                            }
                            tvError.text = friendlyMessage
                            tvError.visibility = View.VISIBLE
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
                        tvError.text = "Google Sign-In not available"
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
                runOnUiThread {
                    val conflict = response.optBoolean("conflict", false)
                    if (conflict) {
                        showGoogleConflictDialog(idToken, response.optString("existingUsername", ""))
                    } else {
                        finishGoogleLogin(response)
                    }
                }
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

    private fun showGoogleConflictDialog(idToken: String, existingUsername: String, authCode: String = "") {
        AlertDialog.Builder(this)
            .setTitle("Account Conflict")
            .setMessage("This Google email is already registered to \"$existingUsername\".\n\nLog in as $existingUsername?")
            .setPositiveButton("Yes, login as $existingUsername") { _, _ ->
                Thread {
                    try {
                        val response = ApiClient.googleLogin(idToken, existingUsername, authCode)
                        runOnUiThread { finishGoogleLogin(response) }
                    } catch (e: Exception) {
                        runOnUiThread {
                            tvError.text = "Failed: ${e.message}"
                            tvError.visibility = View.VISIBLE
                        }
                    }
                }.start()
            }
            .setNegativeButton("Go Back") { _, _ -> /* user cancels */ }
            .setCancelable(true)
            .show()
    }

    /** Handle Google sign-in via Chrome Custom Tabs (devices without Google Play Services). */
    private fun handleGoogleSignInWithAuthCode(authCode: String, redirectUri: String) {
        Thread {
            try {
                val response = ApiClient.googleLogin("", "", authCode, redirectUri)
                runOnUiThread {
                    val conflict = response.optBoolean("conflict", false)
                    if (conflict) {
                        showGoogleConflictDialog("", response.optString("existingUsername", ""), authCode)
                    } else {
                        finishGoogleLogin(response)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread {
                    showSignInError("Google sign-in failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun showSignInError(msg: String) {
        tvError.text = msg
        tvError.visibility = View.VISIBLE
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
