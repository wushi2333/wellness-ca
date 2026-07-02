// Author: Xia Zihang
package iss.nus.edu.sg.ca_application.model

data class CharacterMessage(
    val id: Long,
    val role: String,
    val content: String,
    val emotion: String,
    val createdAt: String,
    val tools: List<String>? = null
)
