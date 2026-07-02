package iss.nus.edu.sg.ca_application.auth

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

class LoginActivity : AppCompatActivity() {

    private lateinit var tvError: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        // 如果已登录直接跳转
        if (TokenManager.getToken(this) != null) {
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

            tvError.visibility = View.GONE  // 每次点击先隐藏错误

            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {
                tvError.text = "Please enter username and password"
                tvError.visibility = View.VISIBLE
                return@setOnClickListener
            }

            ApiClient.login(
                username,
                password,
                object : ApiClient.LoginCallback {

                    override fun onSuccess(response: LoginResponse) {

                        runOnUiThread {

                            tvError.visibility = View.GONE

                            TokenManager.saveToken(
                                this@LoginActivity,
                                response.accessToken,
                                response.tokenType
                            )

                            Toast.makeText(
                                this@LoginActivity,
                                "Login Successful",
                                Toast.LENGTH_SHORT
                            ).show()

                            startActivity(
                                Intent(
                                    this@LoginActivity,
                                    MainActivity::class.java
                                )
                            )

                            finish()
                        }
                    }

                    override fun onFailure(message: String) {

                        runOnUiThread {

                            val friendlyMessage = when (message) {

                                "Bad credentials" -> "Incorrect username or password"

                                "Incorrect username or password" -> "Incorrect username or password"

                                "User not found" -> "User does not exist"

                                else -> "Login failed, please try again"
                            }

                            tvError.text = friendlyMessage
                            tvError.visibility = View.VISIBLE
                        }
                    }
                }
            )
        }

        btnRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}