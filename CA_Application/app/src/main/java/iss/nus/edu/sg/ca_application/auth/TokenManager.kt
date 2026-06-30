package iss.nus.edu.sg.ca_application.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Stores and retrieves JWT access token via SharedPreferences.
 * Token is obtained from POST /login and sent as Authorization: Bearer header.
 */
object TokenManager {

    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_ACCESS_TOKEN = "jwt_access_token"
    private const val KEY_TOKEN_TYPE = "jwt_token_type"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun saveToken(context: Context, accessToken: String, tokenType: String = "bearer") {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_TOKEN_TYPE, tokenType)
            .apply()
    }

    fun getToken(context: Context): String {
        return prefs(context).getString(KEY_ACCESS_TOKEN, "") ?: ""
    }

    fun getTokenType(context: Context): String {
        return prefs(context).getString(KEY_TOKEN_TYPE, "bearer") ?: "bearer"
    }

    fun isLoggedIn(context: Context): Boolean {
        return getToken(context).isNotEmpty()
    }

    fun clearToken(context: Context) {
        prefs(context).edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_TOKEN_TYPE)
            .apply()
    }
}
