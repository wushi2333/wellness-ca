package iss.nus.edu.sg.ca_application.model

/**
 * Author: Wang Songyu
 *
 * Represents a wellness record returned by the backend REST API.
 *
 * Used by:
 * - GET /records
 *
 * This model represents a complete wellness record stored in the
 * database. Unlike WellnessEntry, it includes the unique record ID
 * assigned by the backend.
 * Each wellness record returned by the backend:
 * {
 *   "id": 1,
 *   "userId": 1,
 *   "sleepHours": 7.5,
 *   "exerciseActivity": "Running",
 *   "exerciseDuration": 30,
 *   "recordDate": "2025-07-01",
 *   "notes": "Felt great today",
 *   "createdAt": "2025-07-01T10:00:00"
 * }
 *
 * API Contract: POST /records  (request body)
 * {
 *   "sleepHours": 7.5,
 *   "exerciseActivity": "Running",
 *   "exerciseDuration": 30,
 *   "recordDate": "2025-07-01",
 *   "notes": "Felt great today"
 * }
 *
 * API Contract: PUT /records/{id}  (request body, same shape)
 *
 * API Contract: DELETE /records/{id}  (no body, 204 No Content)
 *
 * All requests require headers:
 *   Authorization: Bearer <accessToken>
 *   X-API-Token:   team-wellness-2025
 */
data class WellnessRecord(

    // Unique record identifier
    val id: Int = 0,

    // Hours of sleep
    val sleepHours: Double,

    // Exercise activity (e.g. Running, Swimming)
    val exerciseActivity: String,

    // Exercise duration in minutes
    val exerciseDuration: Int,

    // Record date (yyyy-MM-dd)
    val recordDate: String,

    // Additional notes
    val notes: String
)