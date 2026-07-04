// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.model

data class CharacterChatResponse(
    val reply: String,
    val emotion: String,
    val sessionId: Long,
    val intent: Map<String, Any?>? = null,
    val tools: List<String>? = null
)
