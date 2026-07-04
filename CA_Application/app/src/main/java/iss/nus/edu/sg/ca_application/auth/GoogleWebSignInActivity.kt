// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.auth

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabsIntent
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.URLDecoder

/**
 * Google OAuth via Chrome Custom Tabs (compliant with Google's "Use secure browsers" policy).
 *
 * Starts a local HTTP server on a random port, opens Chrome with Google's OAuth page,
 * intercepts the redirect via the local server, and returns the auth code.
 */
class GoogleWebSignInActivity : AppCompatActivity() {

    companion object {
        private const val CLIENT_ID = "375016829980-l1gpslqj0cltlc5aqf2f403c52oepeg7.apps.googleusercontent.com"

        private const val AUTH_URL = "https://accounts.google.com/o/oauth2/v2/auth" +
            "?client_id=$CLIENT_ID" +
            "&redirect_uri=REDIRECT_PLACEHOLDER" +
            "&response_type=code" +
            "&scope=email%20profile%20openid" +
            "&access_type=offline"
    }

    private var localServer: ServerSocket? = null
    private var redirectUri: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val port = startLocalServer()
        if (port == 0) {
            finishWithError("Failed to start local server")
            return
        }

        redirectUri = "http://localhost:$port/oauth2callback"
        val url = AUTH_URL.replace("REDIRECT_PLACEHOLDER", redirectUri)

        try {
            CustomTabsIntent.Builder().build().launchUrl(this, Uri.parse(url))
        } catch (e: Exception) {
            finishWithError("Chrome not available: ${e.message}")
        }
    }

    private fun startLocalServer(): Int {
        return try {
            localServer = ServerSocket(0)
            val port = localServer!!.localPort
            Thread {
                try {
                    while (true) {
                        val socket = localServer!!.accept()
                        val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                        val requestLine = reader.readLine() ?: continue
                        if (requestLine.contains("/oauth2callback")) {
                            val code = extractCodeFromRequestLine(requestLine)
                            val body = if (code != null)
                                "<html><body><h3>Sign in successful</h3><p>You can close this tab.</p></body></html>"
                            else
                                "<html><body><h3>Sign in failed</h3></body></html>"
                            val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: ${body.length}\r\n\r\n$body"
                            socket.getOutputStream().write(resp.toByteArray())
                            socket.close()
                            if (code != null) {
                                runOnUiThread { returnCode(code) }
                                break
                            }
                        } else {
                            val resp = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\nContent-Length: 2\r\n\r\nOK"
                            socket.getOutputStream().write(resp.toByteArray())
                            socket.close()
                        }
                    }
                } catch (_: Exception) {}
            }.start()
            port
        } catch (e: Exception) {
            Log.e("GoogleWebSignIn", "Server start failed", e)
            0
        }
    }

    private fun extractCodeFromRequestLine(requestLine: String): String? {
        return try {
            val path = requestLine.split(" ")[1]
            val query = path.substringAfter("?")
            var code: String? = null
            for (param in query.split("&")) {
                val parts = param.split("=", limit = 2)
                if (parts[0] == "code" && parts.size == 2) {
                    code = URLDecoder.decode(parts[1], "UTF-8")
                    break
                }
            }
            code
        } catch (_: Exception) { null }
    }

    private fun returnCode(authCode: String) {
        val data = Intent().apply {
            putExtra("authCode", authCode.trim())
            putExtra("redirectUri", redirectUri)
        }
        setResult(RESULT_OK, data)
        finish()
    }

    private fun finishWithError(msg: String) {
        setResult(RESULT_CANCELED)
        finish()
        android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show()
    }

    override fun onDestroy() {
        super.onDestroy()
        try { localServer?.close() } catch (_: Exception) {}
    }
}
