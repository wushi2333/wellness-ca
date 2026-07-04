package iss.nus.edu.sg.ca_application.model

// Author: Yutong Luo, Liu Yu

/**
 * API Contract: POST /login  (response)
 *
 * See LoginRequest.kt for full request/response specification.
 *
 * This class models the JSON response:
 * {
 *   "accessToken": "...",
 *   "tokenType": "bearer",
 *   "userId": 1,
 *   "username": "alice"
 * }
 */

data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val userId: Long,
    val username: String,
    val email: String? = null
)
