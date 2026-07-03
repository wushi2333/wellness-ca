# Backend API Testing

Author: Yutong Luo

This guide tests the Spring Boot backend authentication and wellness record APIs.

The examples use `http://localhost:8000`. For the deployed server, replace it with the actual backend host, for example `http://152.42.181.66:8000`.

JSON field names use camelCase because this is the Spring Boot backend version.

## Required Headers

All requests need the gateway token:

```http
X-API-Token: team-wellness-2025
Content-Type: application/json
```

Protected APIs also need JWT:

```http
Authorization: Bearer <accessToken>
```

Error responses use a shared JSON shape:

```json
{
  "detail": "Error message"
}
```

## Register

```bash
curl -i -X POST http://localhost:8000/register \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -d '{"username":"yutong_test","password":"123456"}'
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
  -d '{"username":"yutong_test","password":"123456"}'
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

Save the `accessToken` value for the next requests.

Android should store this value locally and send it as `Authorization: Bearer <accessToken>`.

## Create Wellness Record

```bash
curl -i -X POST http://localhost:8000/records \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "sleepHours": 7.5,
    "exerciseActivity": "Running",
    "exerciseDuration": 30,
    "moodScore": 4,
    "recordDate": "2026-06-30",
    "notes": "Felt good today"
  }'
```

Expected result:

```json
{
  "message": "Created",
  "id": 1
}
```

`moodScore` is optional. If Android has not implemented mood input yet, the request can omit this field.

## List My Wellness Records

```bash
curl -i -X GET http://localhost:8000/records \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>"
```

Expected result:

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

The response does not include `userId`. The backend uses the JWT to decide which user's records to return.

## Update Wellness Record

```bash
curl -i -X PUT http://localhost:8000/records/1 \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "sleepHours": 8,
    "exerciseActivity": "Walking",
    "exerciseDuration": 20,
    "moodScore": 5,
    "recordDate": "2026-06-30",
    "notes": "Updated notes"
  }'
```

Expected result:

```json
{
  "message": "Updated"
}
```

## Delete Wellness Record

```bash
curl -i -X DELETE http://localhost:8000/records/1 \
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

Invalid gateway token should return 403:

```bash
curl -i -X GET http://localhost:8000/records \
  -H "X-API-Token: wrong-token" \
  -H "Authorization: Bearer <accessToken>"
```

Invalid wellness data should return 400:

```bash
curl -i -X POST http://localhost:8000/records \
  -H "Content-Type: application/json" \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer <accessToken>" \
  -d '{
    "sleepHours": 30,
    "exerciseActivity": "Running",
    "exerciseDuration": -5,
    "moodScore": 9,
    "recordDate": "2026-06-30",
    "notes": "Invalid values"
}'
```

Expected result:

```json
{
  "detail": "sleepHours: must be less than or equal to 24.0; exerciseDuration: must be greater than or equal to 0; moodScore: must be less than or equal to 5"
}
```

Another user's record should return 404 on update or delete because the service checks ownership before changing data.

Expected result:

```json
{
  "detail": "Record not found"
}
```
