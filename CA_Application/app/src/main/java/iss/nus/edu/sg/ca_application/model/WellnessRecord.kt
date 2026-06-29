package iss.nus.edu.sg.ca_application.model

/**
 * API Contract: GET /records  (response item)
 *
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
