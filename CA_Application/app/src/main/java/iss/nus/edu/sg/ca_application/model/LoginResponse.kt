package iss.nus.edu.sg.ca_application.model

// Author: Yutong Luo, Liu Yu

/**
 * API Contract: POST /login  (response)
 *
 * See LoginRequest.kt for full request/response specification.
 *
 * This class models the JSON response:
 * {
 *   "access_token": "...",
 *   "token_type": "bearer"
 * }
 */

data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val userId: Long,
    val username: String
)
