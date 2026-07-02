package iss.nus.edu.sg.ca_application

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import iss.nus.edu.sg.ca_application.auth.LoginActivity
import iss.nus.edu.sg.ca_application.auth.TokenManager

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        val btnLogout = findViewById<Button>(R.id.btnLogout)

        btnLogout.setOnClickListener {

            // 清除保存的 JWT
            TokenManager.clearToken(this)

            // 跳回登录页面
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)

            // 关闭 MainActivity
            finish()

        }
    }
}