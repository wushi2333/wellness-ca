package iss.nus.edu.sg.ca_application.model

data class LoginResponse(
    val accessToken: String,
    val tokenType: String,
    val userId: Long,
    val username: String
)