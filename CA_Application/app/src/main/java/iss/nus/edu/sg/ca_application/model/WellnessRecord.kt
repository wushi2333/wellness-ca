package iss.nus.edu.sg.ca_application.model

/**
 * API Contract: GET /records  (response item)
 *
 * Each wellness record returned by the backend:
 * {
 *   "id": 1,
 *   "user_id": 1,
 *   "sleep_hours": 7.5,
 *   "exercise_activity": "Running",
 *   "exercise_duration": 30,
 *   "record_date": "2025-07-01",
 *   "notes": "Felt great today",
 *   "created_at": "2025-07-01T10:00:00"
 * }
 *
 * API Contract: POST /records  (request body)
 * {
 *   "sleep_hours": 7.5,
 *   "exercise_activity": "Running",
 *   "exercise_duration": 30,
 *   "record_date": "2025-07-01",
 *   "notes": "Felt great today"
 * }
 *
 * API Contract: PUT /records/{id}  (request body, same shape)
 *
 * API Contract: DELETE /records/{id}  (no body, 204 No Content)
 *
 * All requests require headers:
 *   Authorization: Bearer <access_token>
 *   X-API-Token:   team-wellness-2025
 */
