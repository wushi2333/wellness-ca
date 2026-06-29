package iss.nus.edu.sg.ca_application.auth

/**
 * Manages JWT access token persistence and retrieval.
 *
 * Token lifecycle:
 *   1. LoginActivity calls POST /login → receives { "accessToken": "...", "tokenType": "bearer" }
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
 * Key names: "jwtAccessToken", "jwtTokenType"
 */
