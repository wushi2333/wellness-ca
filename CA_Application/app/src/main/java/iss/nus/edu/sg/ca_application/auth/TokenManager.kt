package iss.nus.edu.sg.ca_application.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Manages JWT access token persistence and retrieval.
 *
 * Token lifecycle:
 *   1. LoginActivity calls POST /login → receives { "access_token": "...", "token_type": "bearer" }
 *   2. saveToken(...) stores the token in SharedPreferences
 *   3. getToken() retrieves it for every authenticated API call
 *   4. clearToken() is called on logout or when a 401 is received
 *
 * JWT claims (decoded payload):
 *   {
 *     "sub": "1",           // user ID as string
 *     "exp": 1782433600     // expiration timestamp (Unix epoch)
 *   }
 *
 * The JWT secret key is stored ONLY on the server (152.42.181.66).
 * It never appears in the Android codebase.
 *
 * Storage: SharedPreferences (private to the app)
 * Key names: "jwt_access_token", "jwt_token_type"
 */
class TokenManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun saveToken(accessToken: String, tokenType: String = "bearer") {
        prefs.edit()
            .putString(KEY_TOKEN, accessToken)
            .putString(KEY_TYPE, tokenType)
            .apply()
    }

    fun getToken(): String? = prefs.getString(KEY_TOKEN, null)

    fun getAuthHeader(): String? {
        val token = getToken() ?: return null
        return "Bearer $token"
    }

    fun clearToken() {
        prefs.edit().remove(KEY_TOKEN).remove(KEY_TYPE).apply()
    }

    companion object {
        private const val PREFS_NAME = "wellness_auth"
        private const val KEY_TOKEN = "jwtAccessToken"
        private const val KEY_TYPE = "jwtTokenType"
    }
}