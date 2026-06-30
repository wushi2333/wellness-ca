package iss.nus.edu.sg.ca_application.auth

import android.content.Context
import android.content.SharedPreferences

/**
 * Author: Wang Songyu
 *
 * Manages JWT access token persistence using SharedPreferences.
 *
 * Token lifecycle:
 * 1. LoginActivity calls POST /login and receives:
 *      {
 *          "accessToken": "...",
 *          "tokenType": "bearer",
 *          "userId": 1,
 *          "username": "alice"
 *      }
 * 2. saveToken() stores the token locally.
 * 3. getToken() retrieves the token for authenticated API requests.
 * 4. clearToken() removes the token on logout or when a 401 Unauthorized
 *    response is received.
 *
 * JWT payload example:
 * {
 *     "sub": "1",
 *     "exp": 1782433600
 * }
 *
 * Notes:
 * - The JWT secret key is stored ONLY on the backend server.
 * - Android stores only the access token.
 * - SharedPreferences is used for local persistence.
 */
object TokenManager {

    private const val PREFS_NAME = "auth_prefs"
    private const val KEY_ACCESS_TOKEN = "jwt_access_token"
    private const val KEY_TOKEN_TYPE = "jwt_token_type"

    private fun prefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Saves the access token returned from the login API.
     *
     * @param context Android context.
     * @param accessToken JWT access token.
     * @param tokenType Token type returned by the backend (usually "bearer").
     */
    fun saveToken(context: Context, accessToken: String, tokenType: String = "bearer") {
        prefs(context).edit()
            .putString(KEY_ACCESS_TOKEN, accessToken)
            .putString(KEY_TOKEN_TYPE, tokenType)
            .apply()
    }

    /**
     * Returns the stored JWT access token.
     *
     * If no token exists, an empty string is returned instead of null,
     * making it easier for ApiClient methods to use directly.
     */
    fun getToken(context: Context): String {
        return prefs(context).getString(KEY_ACCESS_TOKEN, "") ?: ""
    }

    /**
     * Returns the stored token type.
     *
     * The default value is "bearer".
     */
    fun getTokenType(context: Context): String {
        return prefs(context).getString(KEY_TOKEN_TYPE, "bearer") ?: "bearer"
    }

    /**
     * Checks whether the user is currently logged in.
     *
     * This only verifies whether a token exists locally.
     * Token expiration is validated by the backend.
     */
    fun isLoggedIn(context: Context): Boolean {
        return getToken(context).isNotEmpty()
    }

    /**
     * Clears all locally stored authentication information.
     *
     * This should be called when:
     * - The user logs out.
     * - The backend returns HTTP 401 Unauthorized.
     */
    fun clearToken(context: Context) {
        prefs(context).edit()
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_TOKEN_TYPE)
            .apply()
    }
}