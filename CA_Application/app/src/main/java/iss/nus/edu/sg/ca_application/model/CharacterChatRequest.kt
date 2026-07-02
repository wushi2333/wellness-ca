// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.model

data class CharacterChatRequest(
    val message: String,
    val mode: String,
    val sessionId: Long? = null
)
