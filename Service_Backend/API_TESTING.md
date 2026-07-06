# Backend API Testing

Author: Yutong Luo

This guide tests the Spring Boot backend authentication, JWT protection, split wellness record APIs, and user data isolation.

The examples use `http://localhost:8000`. For the deployed server, replace it with the actual backend host, for example `http://152.42.181.66:8000`.

JSON field names use camelCase.

## Required Headers

Every request needs the gateway token:

```http
X-API-Token: team-wellness-2025
Content-Type: application/json
```

Protected APIs also need JWT:

```http
Authorization: Bearer <accessToken>
```

Error responses use this shared JSON shape:

```json
{
  "detail": "Error message"
}
```

## Register

Passwords must be at least 8 characters and contain at least one letter and one digit.

```bash
curl -i -X POST http://localhost:8000/register \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -d '{"username":"yutong_test","password":"abc12345","email":"yutong_test@example.com"}'
```

Expected result:

```json
{
  "message": "Registered",
  "userId": 1
}
```

## Login

```bash
curl -i -X POST http://localhost:8000/login \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -d '{"username":"yutong_test","password":"abc12345"}'
```

Expected result:

```json
{
  "accessToken": "...",
  "tokenType": "bearer",
  "userId": 1,
  "username": "yutong_test"
}
```

Save the `accessToken` value for protected API tests.

Android stores this token locally and sends it as:

```http
Authorization: Bearer <accessToken>
```

## Create Sleep Record

```bash
curl -i -X POST http://localhost:8000/sleep-records \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "sleepHours": 7.5,
    "sleepTime": "23:30",
    "wakeTime": "07:00",
    "moodScore": 4,
    "recordDate": "2026-07-06",
    "notes": "Slept well"
  }'
```

Expected result:

```json
{
  "message": "Created",
  "id": 1
}
```

## Create Exercise Record

```bash
curl -i -X POST http://localhost:8000/exercise-records \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "exerciseActivity": "Running",
    "exerciseDuration": 30,
    "recordDate": "2026-07-06",
    "notes": "Easy run"
  }'
```

Expected result:

```json
{
  "message": "Created",
  "id": 1
}
```

## List My Daily Wellness Records

`GET /records` returns paginated daily records. Each daily record can contain one sleep record and multiple exercise records.

```bash
curl -i -X GET "http://localhost:8000/records?page=0&size=20" \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>"
```

Expected result:

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

The response does not expose `userId`. The backend uses the JWT to decide which user's data to return.

## Update Sleep Record

```bash
curl -i -X PUT http://localhost:8000/sleep-records/1 \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "sleepHours": 8,
    "sleepTime": "23:00",
    "wakeTime": "07:00",
    "moodScore": 5,
    "recordDate": "2026-07-06",
    "notes": "Updated sleep notes"
  }'
```

Expected result:

```json
{
  "message": "Updated"
}
```

## Update Exercise Record

```bash
curl -i -X PUT http://localhost:8000/exercise-records/1 \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "exerciseActivity": "Walking",
    "exerciseDuration": 45,
    "recordDate": "2026-07-06",
    "notes": "Updated exercise notes"
  }'
```

Expected result:

```json
{
  "message": "Updated"
}
```

## Delete Sleep Or Exercise Record

```bash
curl -i -X DELETE http://localhost:8000/sleep-records/1 \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>"
```

```bash
curl -i -X DELETE http://localhost:8000/exercise-records/1 \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>"
```

Expected result:

```json
{
  "message": "Deleted"
}
```

## Negative Tests

No JWT should return 401:

```bash
curl -i -X GET http://localhost:8000/records \
  -H "X-API-Token: team-wellness-2025"
```

Invalid JWT should return 401:

```bash
curl -i -X GET http://localhost:8000/records \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer invalid-token"
```

Invalid gateway token should return 403:

```bash
curl -i -X GET http://localhost:8000/records \
  -H "X-API-Token: wrong-token" \
  -H "Authorization: Bearer <accessToken>"
```

Invalid sleep data should return 400:

```bash
curl -i -X POST http://localhost:8000/sleep-records \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "sleepHours": 30,
    "moodScore": 9,
    "recordDate": "2026-07-06",
    "notes": "Invalid values"
  }'
```

Expected result:

```json
{
  "detail": "sleepHours: must be less than or equal to 24.0; moodScore: must be less than or equal to 5"
}
```

Invalid exercise data should return 400:

```bash
curl -i -X POST http://localhost:8000/exercise-records \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "exerciseActivity": "",
    "exerciseDuration": 0,
    "recordDate": "2026-07-06",
    "notes": "Invalid values"
  }'
```

Expected result includes validation errors for `exerciseActivity` and `exerciseDuration`.

## Ownership Tests

Create two users and save their tokens as `TOKEN_A` and `TOKEN_B`.

User A creates a sleep record:

```bash
curl -i -X POST http://localhost:8000/sleep-records \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer $TOKEN_A" \
  -d '{"sleepHours":7,"recordDate":"2026-07-06","notes":"User A sleep"}'
```

Then User B tries to update or delete User A's sleep record:

```bash
curl -i -X PUT http://localhost:8000/sleep-records/<USER_A_SLEEP_ID> \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer $TOKEN_B" \
  -d '{"sleepHours":8,"recordDate":"2026-07-06","notes":"Should fail"}'
```

```bash
curl -i -X DELETE http://localhost:8000/sleep-records/<USER_A_SLEEP_ID> \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer $TOKEN_B"
```

Expected result:

```json
{
  "detail": "Sleep record not found"
}
```

Repeat the same test for `exercise-records`. User B should get 404 when trying to update or delete User A's exercise record.

## Deleted Account Token Test

JWT tokens include a user id. The backend now checks that the user still exists before accepting the token.

With a disposable test account:

1. Register and login.
2. Save the returned token.
3. Delete the account:

```bash
curl -i -X DELETE http://localhost:8000/auth/account \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>"
```

4. Try to call a protected API with the old token:

```bash
curl -i -X GET http://localhost:8000/records \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>"
```

Expected result: 401 Unauthorized.
