package iss.nus.edu.sg.ca_application.model

/**
 * API Contract: POST /chat
 *
 * Request body (JSON):
 * {
 *   "message": "I've been sleeping poorly this week. Any advice?"
 * }
 *
 * Response body (JSON, 200 OK):
 * {
 *   "reply": "Based on your records, try reducing screen time before bed..."
 * }
 *
 * Required headers:
 *   Authorization: Bearer <access_token>
 *   X-API-Token:   team-wellness-2025
 */
