// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.model

data class UserProfileData(
    val userId: Long,
    val username: String,
    val email: String?,
    val provider: String?,
    val avatarUrl: String?,
    val nickname: String?,
    val heightCm: Int?,
    val age: Int?,
    val weightKg: Double?
)
