# Backend Notes

Author: Yutong Luo

This note explains the Spring Boot backend design for database persistence, JWT-protected access, and Android integration.

## Scope

This backend section covers:

- User registration and login.
- JWT-protected REST APIs for Android.
- MySQL persistence through Spring Data JPA.
- Split wellness record schema for daily, sleep, and exercise data.
- User data isolation for wellness, character sessions, and recommendations.
- Request validation before data is saved.

The Live2D character, AI chatbot, RAG sidecar, and agentic AI sidecar are kept as separate modules, but their protected endpoints still use the same JWT user identity.

## Important API Rules

All API requests must include the gateway token:

```http
X-API-Token: team-wellness-2025
```

All protected API requests must also include JWT:

```http
Authorization: Bearer <accessToken>
```

Public endpoints:

```text
POST /register
POST /login
POST /auth/google
```

Main protected Android endpoints:

```text
GET    /records
POST   /sleep-records
PUT    /sleep-records/{id}
DELETE /sleep-records/{id}
POST   /exercise-records
PUT    /exercise-records/{id}
DELETE /exercise-records/{id}
POST   /character/chat
POST   /character/agent
GET    /character/sessions
GET    /character/sessions/{id}/messages
DELETE /character/sessions/{id}
POST   /agent/recommend
GET    /agent/recommend/history
DELETE /agent/recommend/{id}
GET    /user
PUT    /profile
POST   /profile/avatar
POST   /auth/change-password
PUT    /auth/email
DELETE /auth/account
```

The Thymeleaf web UI uses session-based auth under `/web/**`; Android uses JWT.

## Login Response

Login returns camelCase JSON:

```json
{
  "accessToken": "...",
  "tokenType": "bearer",
  "userId": 1,
  "username": "alice"
}
```

Android stores `accessToken` locally and sends it in the `Authorization` header for protected APIs.

## JWT Flow

```text
POST /login
  -> AuthService verifies username/email and BCrypt password
  -> JwtTokenProvider creates token with user id as subject
  -> Android stores accessToken
  -> Protected API sends Authorization: Bearer <accessToken>
  -> JwtAuthFilter validates signature and expiry
  -> JwtAuthFilter checks that the user still exists in users table
  -> request attribute userId is set
  -> controllers/services use that authenticated userId
```

The backend does not trust `userId` from Android request bodies.

Logout is currently stateless: Android clears the local token. There is no token blacklist in this version.

## Database Schema

`users` includes:

```text
id
username
email
hashedPassword
role
provider
providerId
createdAt
```

`wellness_records` is a daily journal table:

```text
id
userId
recordDate
sleepRecordId
createdAt
updatedAt
```

Constraints:

- One user can have many daily wellness records.
- `userId + recordDate` is unique.
- `sleepRecordId` links to one optional sleep record for that day.

`sleep_records` includes:

```text
id
sleepHours
sleepTime
wakeTime
moodScore
notes
createdAt
updatedAt
```

`exercise_records` includes:

```text
id
dailyRecordId
exerciseActivity
exerciseDuration
notes
createdAt
```

One daily wellness record can have multiple exercise records.

`user_profile`, `character_sessions`, `character_messages`, `character_user_profile`, `chat_history`, and `recommendations` store profile, chat, memory, and recommendation data.

Spring Boot currently uses:

```properties
spring.jpa.hibernate.ddl-auto=update
```

This is acceptable during development. Before final production deployment, the team should confirm the MySQL schema and avoid accidental destructive migrations.

## Wellness Record JSON

Create sleep:

```json
{
  "sleepHours": 7.5,
  "sleepTime": "23:30",
  "wakeTime": "07:00",
  "moodScore": 4,
  "recordDate": "2026-07-06",
  "notes": "Slept well"
}
```

Create exercise:

```json
{
  "exerciseActivity": "Running",
  "exerciseDuration": 30,
  "recordDate": "2026-07-06",
  "notes": "Easy run"
}
```

List response from `GET /records` is paginated:

```json
{
  "content": [
    {
      "dailyRecordId": 1,
      "recordDate": "2026-07-06",
      "sleep": {
        "id": 1,
        "sleepHours": 7.5,
        "sleepTime": "23:30",
        "wakeTime": "07:00",
        "moodScore": 4,
        "notes": "Slept well"
      },
      "exercises": [
        {
          "id": 1,
          "exerciseActivity": "Running",
          "exerciseDuration": 30,
          "notes": "Easy run"
        }
      ]
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "last": true
}
```

## Validation Rules

- `username` is required and must be 3 to 50 characters.
- `password` is required, must be at least 8 characters, and must contain at least one letter and one digit.
- `sleepHours` is required and must be 0 to 24.
- `sleepTime` and `wakeTime` are optional `HH:mm` style strings with max length 5.
- `moodScore` is optional, but if provided must be 1 to 5.
- `exerciseActivity` is required and must not exceed 100 characters.
- `exerciseDuration` is required and must be 1 to 1440 minutes.
- `recordDate` must use `yyyy-MM-dd`.
- `notes` must not exceed 1000 characters.

## User Data Isolation

Clients must not send `userId` for data ownership.

The backend uses JWT to identify the current user:

```text
JWT subject -> userId -> request attribute -> ownership query
```

Wellness list queries use authenticated `userId`.

Sleep update/delete checks ownership by finding a daily wellness record where:

```text
daily.userId = current user id
daily.sleepRecordId = requested sleep id
```

Exercise update/delete checks ownership by:

```text
exercise.id -> exercise.dailyRecordId -> wellness_records.id + current user id
```

Character session message reads and session deletes use `sessionId + userId`.

Recommendation delete uses `recommendationId + userId`.

If a record belongs to another user, the backend returns 404 instead of revealing whether that record exists.

Error responses use a shared JSON shape:

```json
{
  "detail": "Error message"
}
```

## Environment Variables

The server must provide:

```text
DB_HOST
DB_PORT
DB_NAME
DB_USER
DB_PASSWORD
JWT_SECRET_KEY
ACCESS_TOKEN_EXPIRE_MINUTES
API_GATEWAY_TOKEN
DEEPSEEK_API_KEY
VOLCANO_TTS_APPID
VOLCANO_TTS_TOKEN
VOLCANO_TTS_SPEAKER
```

Do not commit real database passwords, API keys, JWT secrets, or TTS secrets.

`JWT_SECRET_KEY` must be at least 32 characters.

## Test Guide

Use `API_TESTING.md` to test register, login, JWT access, validation errors, split wellness APIs, and ownership protection.

Compile and run unit tests:

```bash
cd Service_Backend
mvn clean test
```

Run backend:

```bash
cd Service_Backend
mvn package -DskipTests
java -jar target/wellness-backend-1.0.jar
```

## Notes For Teammates

- Android should store only the JWT access token, not passwords.
- Android should not rely on `userId` in wellness record responses.
- Android logout can clear the local JWT token.
- If endpoint paths change, update `ApiClient.kt`, `API_TESTING.md`, and any project API reference together.
- The gateway token is an extra guard, but the real user-level authorization comes from JWT and backend ownership checks.
