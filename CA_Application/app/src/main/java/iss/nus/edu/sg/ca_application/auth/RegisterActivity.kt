// Liu Yu
package iss.nus.edu.sg.ca_application.auth

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.ca_application.R
import iss.nus.edu.sg.ca_application.network.ApiClient

class RegisterActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val etUsername = findViewById<EditText>(R.id.etUsername)
        val etPassword = findViewById<EditText>(R.id.etPassword)
        val btnRegister = findViewById<Button>(R.id.btnRegister)

        btnRegister.setOnClickListener {

            val username = etUsername.text.toString().trim()
            val password = etPassword.text.toString().trim()

            if (username.isEmpty() || password.isEmpty()) {

                Toast.makeText(
                    this,
                    "Please enter username and password",
                    Toast.LENGTH_SHORT
                ).show()

                return@setOnClickListener
            }

            ApiClient.register(
                username,
                password,
                object : ApiClient.RegisterCallback {

                    override fun onSuccess(message: String) {

                        runOnUiThread {

                            Toast.makeText(
                                this@RegisterActivity,
                                message,
                                Toast.LENGTH_SHORT
                            ).show()

                            // 注册成功后返回登录页面
                            finish()

                        }

                    }

                    override fun onFailure(message: String) {

                        runOnUiThread {

                            Toast.makeText(
                                this@RegisterActivity,
                                message,
                                Toast.LENGTH_LONG
                            ).show()

                        }

                    }

                }
            )

        }

    }

}