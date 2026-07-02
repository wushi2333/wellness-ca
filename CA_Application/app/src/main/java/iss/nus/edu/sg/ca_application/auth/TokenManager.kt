// Liu Yu
package iss.nus.edu.sg.ca_application.auth

import android.content.Context

object TokenManager {

    private const val PREF_NAME = "WellnessPrefs"

    private const val KEY_TOKEN = "jwtAccessToken"

    private const val KEY_TYPE = "jwtTokenType"

    fun saveToken(
        context: Context,
        token: String,
        tokenType: String
    ) {

        val prefs = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

        prefs.edit()
            .putString(KEY_TOKEN, token)
            .putString(KEY_TYPE, tokenType)
            .apply()
    }

    fun getToken(context: Context): String? {

        val prefs = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

        return prefs.getString(KEY_TOKEN, null)
    }

    fun getTokenType(context: Context): String? {

        val prefs = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

        return prefs.getString(KEY_TYPE, null)
    }

    fun clearToken(context: Context) {

        val prefs = context.getSharedPreferences(
            PREF_NAME,
            Context.MODE_PRIVATE
        )

        prefs.edit().clear().apply()
    }

}