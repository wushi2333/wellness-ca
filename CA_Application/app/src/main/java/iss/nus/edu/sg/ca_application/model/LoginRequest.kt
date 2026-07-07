// Author: Liu Yu, Xia Zihang
package iss.nus.edu.sg.ca_application.model

// Author: Liu Yu

/**
 * API Contract: POST /login
 *
 * Request body (JSON):
 * {
 *   "username": "user123",
 *   "password": "mypassword"
 * }
 *
 * Response body (JSON, 200 OK):
 * {
 *   "accessToken": "eyJhbGciOi...",
 *   "tokenType": "bearer"
 * }
 *
 * Error response (JSON, 400):
 * {
 *   "detail": "Incorrect username or password"
 * }
 *
 * JWT Token payload claims (decoded):
 * {
 *   "sub": "1",           // user ID as string
 *   "exp": 1782433600     // expiration timestamp (Unix epoch)
 * }
 *
 * All subsequent authenticated requests MUST include headers:
 *   Authorization: Bearer <accessToken>
 *   X-API-Token:   team-wellness-2025
 */

data class LoginRequest(
    val username: String,
    val password: String
)
