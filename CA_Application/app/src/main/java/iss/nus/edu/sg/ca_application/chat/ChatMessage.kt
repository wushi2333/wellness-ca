package iss.nus.edu.sg.ca_application.chat

/**
 * Author: Huang Qianer
 * Represents a single message in the chat UI.
 */
data class ChatMessage(
    val content: String,
    val isUser: Boolean,
    val timestamp: Long = System.currentTimeMillis()
)
