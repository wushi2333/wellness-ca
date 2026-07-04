// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.model

data class ChangePasswordRequest(
    val oldPassword: String,
    val newPassword: String
)
