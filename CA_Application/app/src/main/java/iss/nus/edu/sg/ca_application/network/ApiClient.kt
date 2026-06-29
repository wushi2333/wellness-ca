package iss.nus.edu.sg.ca_application.network

/**
 * Centralized HTTP client for all backend API calls.
 *
 * Server:
 *   Ubuntu 24.04 running Spring Boot on port 8000
 *
 * Required headers on EVERY authenticated request:
 *   Authorization: Bearer <jwtAccessToken>
 *   X-API-Token:   team-wellness-2025
 *   Content-Type:  application/json
 *
 * The API gateway token (team-wellness-2025) is a shared team secret.
 * It is NOT the DeepSeek API key — that never leaves the server.
 *
 * Endpoints consumed by the Android app:
 *   POST   /login              — authenticate and receive JWT
 *   POST   /register           — create new user account
 *   GET    /records             — list user's wellness records
 *   POST   /records             — create a new wellness record
 *   PUT    /records/{id}        — update an existing record
 *   DELETE /records/{id}        — delete a record
 *   POST   /chat                — send a message to the AI chatbot
 *   GET    /recommendations     — retrieve AI-generated recommendations
 *
 * Error handling:
 *   HTTP 401 → JWT expired or invalid → redirect to LoginActivity
 *   HTTP 403 → API gateway token rejected
 *   HTTP 4xx/5xx → show user-friendly Toast/Dialog
 *
 * Threading:
 *   All network calls MUST run on a background thread (Thread { }).
 *   UI updates MUST use runOnUiThread { }.
 *
 * Dependencies:
 *   java.net.HttpURLConnection (as taught in 07_Image_Download.pdf)
 *   org.json.JSONObject (for JSON parsing)
 */

const val BASE_URL = "http://152.42.181.66:8000"
const val API_GATEWAY_TOKEN = "team-wellness-2025"
