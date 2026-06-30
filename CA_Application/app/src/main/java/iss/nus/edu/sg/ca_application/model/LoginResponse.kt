package iss.nus.edu.sg.ca_application.model

/**
 * API Contract: POST /login  (response)
 *
 * See LoginRequest.kt for full request/response specification.
 *
 * This class models the JSON response:
 * {
 *   "accessToken": "...",
 *   "tokenType": "bearer",
 *   "userId": 1,
 *   "username": "alice"
 * }
 *
 * Updated by Yutong Luo: login now returns userId and username
 * so Android can store them locally without decoding the JWT.
 */
