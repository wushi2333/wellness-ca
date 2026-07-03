# Backend Notes

Author: Yutong Luo

This note explains the Spring Boot backend changes for database persistence, JWT-protected access, and Android integration.

## Scope

This backend section covers:

- User registration and login.
- JWT-protected wellness record APIs.
- MySQL persistence through Spring Data JPA.
- User data isolation for wellness records.
- Request validation before data is saved.

The AI chatbot, RAG sidecar, and agentic AI sidecar are kept as separate modules.

## Important API Rules

All requests must include the gateway token:

```http
X-API-Token: team-wellness-2025
```

All protected requests must also include JWT:

```http
Authorization: Bearer <accessToken>
```

Public endpoints:

```text
POST /register
POST /login
```

Protected endpoints:

```text
GET    /records
POST   /records
PUT    /records/{id}
DELETE /records/{id}
POST   /chat
GET    /recommendations
POST   /agent/recommendation
```

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

Android should store `accessToken` and send it in the `Authorization` header for protected APIs.

## Wellness Record JSON

Create and update requests use:

```json
{
  "sleepHours": 7.5,
  "exerciseActivity": "Running",
  "exerciseDuration": 30,
  "moodScore": 4,
  "recordDate": "2026-06-30",
  "notes": "Felt good today"
}
```

Validation rules:

- `sleepHours` is required and must be 0 to 24.
- `exerciseDuration` is required and must not be negative.
- `moodScore` is optional, but if provided must be 1 to 5.
- `recordDate` is required and must use `yyyy-MM-dd`.
- `notes` must not exceed 1000 characters.

List response does not expose `userId`:

```json
[
  {
    "id": 1,
    "sleepHours": 7.5,
    "exerciseActivity": "Running",
    "exerciseDuration": 30,
    "moodScore": 4,
    "recordDate": "2026-06-30",
    "notes": "Felt good today",
    "createdAt": "...",
    "updatedAt": "..."
  }
]
```

## User Data Isolation

Clients must not send `userId` for wellness record ownership.

The backend uses the JWT to identify the current user:

```text
JWT subject -> userId -> request attribute -> database query
```

Wellness records are always queried by the authenticated `userId`.

Update and delete first check that the record belongs to the authenticated user. If it does not, the backend returns 404.

The service uses `findByIdAndUserId(id, userId)` for update and delete. This means the database query only returns a record when both the record ID and authenticated user ID match.

Error responses use a shared JSON shape:

```json
{
  "detail": "Error message"
}
```

## Database Fields

`users` includes:

```text
id
username
hashedPassword
role
createdAt
```

`wellness_records` includes:

```text
id
userId
sleepHours
exerciseActivity
exerciseDuration
moodScore
recordDate
notes
createdAt
updatedAt
```

Spring Boot currently uses:

```properties
spring.jpa.hibernate.ddl-auto=update
```

This can update table structure during development. Before final deployment, the team should confirm the production MySQL schema and avoid dropping existing data.

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
```

Do not commit real database passwords, API keys, or JWT secrets.

`JWT_SECRET_KEY` must be at least 32 characters.

## Test Guide

Use `API_TESTING.md` to test register, login, JWT access, validation errors, and wellness record ownership.

Compile check:

```bash
cd Service_Backend
mvn test
```

Run backend:

```bash
cd Service_Backend
mvn package -DskipTests
java -jar target/wellness-backend-1.0.jar
```

## Notes For Teammates

- JSON uses camelCase, for example `accessToken`, `sleepHours`, and `moodScore`.
- Android should not rely on `userId` in wellness record responses.
- Android logout can clear the local JWT token. There is no server-side token blacklist in this version.
- `moodScore` is optional for backward compatibility with older Android code.
- If the team changes endpoint paths, update `ApiClient.kt`, `API_TESTING.md`, and `docs/api-reference.pdf` together.
