package iss.nus.edu.sg.ca_application.model

/**
 * API Contract: GET /recommendations
 *
 * Each recommendation returned by the backend:
 * {
 *   "id": 1,
 *   "content": "Based on your sleep trends, try going to bed 30 min earlier...",
 *   "created_at": "2025-07-01T10:00:00"
 * }
 *
 * Required headers:
 *   Authorization: Bearer <access_token>
 *   X-API-Token:   team-wellness-2025
 */
