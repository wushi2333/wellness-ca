# Agentic AI Feature — Backend API Spec

**Owner:** Cai Peilin
**For:** Android `agentic/` module integration
**Status:** Draft — endpoints live after PR merge

---

## Overview

Two endpoints power the Agentic AI feature on the Android app:

- `POST /agent/recommend` — runs the agent loop and returns a fresh, evidence-grounded wellness recommendation
- `GET /agent/recommend/history` — returns the user's past agent-generated recommendations

Both endpoints follow the existing auth pattern: `X-API-Token` gateway header plus `Authorization: Bearer <JWT>`.

The agent itself is a plan-and-execute loop over four tools, driven by DeepSeek function calling. On each request the LLM decides which tool to call next based on what it has learned so far. A request typically resolves in 3 iterations and 4 tool calls: load records, analyze sleep, analyze exercise, save recommendation.

---

## 1. POST /agent/recommend

### Request

```
POST http://152.42.181.66:8000/agent/recommend
Headers:
  X-API-Token: team-wellness-2025
  Authorization: Bearer <jwt>
Body: (empty)
```

### Response 200

```json
{
  "recommendation": "Your sleep has been declining sharply — down 0.5 hours per night over the past week, averaging just 6.0 hours with 3 nights below 6 hours. Meanwhile, you're 60 minutes short of the weekly 150-minute exercise target. Try a 20-minute evening walk to gently close that gap, but prioritize winding down 30 minutes earlier to reverse the sleep trend.",
  "evidence": [
    {
      "name": "get_recent_wellness_records",
      "summary": "Loaded 7 records over the last 7 days"
    },
    {
      "name": "analyze_sleep_pattern",
      "summary": "Mean 6.0h, trend -0.50h/day, 3 nights below 6.0h"
    },
    {
      "name": "analyze_exercise_pattern",
      "summary": "Total 90 min across 4 days, gap to WHO guideline: 60.0 min"
    },
    {
      "name": "save_recommendation",
      "summary": "Persisted recommendation id=42"
    }
  ],
  "iterations": 3,
  "saved_id": 42
}
```

### Response 502

```json
{
  "detail": "Agent failed: Agent terminated at step 4 without saving."
}
```

Returned when the LLM violates the loop protocol. Treat as transient — UI should offer a retry button.

### Response 401 / 403

Standard JWT / gateway failures, same as other endpoints.

### Latency

Typical: 5–15 seconds (multiple LLM round-trips). UI must show a loading indicator.

---

## 2. GET /agent/recommend/history

### Request

```
GET http://152.42.181.66:8000/agent/recommend/history?limit=10
Headers:
  X-API-Token: team-wellness-2025
  Authorization: Bearer <jwt>
```

`limit` is optional, default 10, capped at 50.

### Response 200

```json
[
  {
    "id": 42,
    "content": "Your sleep has been declining sharply ...",
    "evidence": [{ "name": "...", "summary": "..." }],
    "iterations": 3,
    "created_at": "2026-06-30T08:14:22"
  },
  {
    "id": 41,
    "content": "Earlier recommendation ...",
    "evidence": [{ "name": "...", "summary": "..." }],
    "iterations": 4,
    "created_at": "2026-06-29T08:12:08"
  }
]
```

Newest first.

---

## 3. Kotlin / Retrofit Interface

Suggested data classes and service definition:

```kotlin
// model/AgentRecommendation.kt
data class ToolTrace(
    val name: String,
    val summary: String
)

data class RecommendResponse(
    val recommendation: String,
    val evidence: List<ToolTrace>,
    val iterations: Int,
    val saved_id: Int
)

data class HistoryItem(
    val id: Int,
    val content: String,
    val evidence: List<ToolTrace>,
    val iterations: Int,
    val created_at: String
)

// network/AgentService.kt
interface AgentService {
    @POST("agent/recommend")
    suspend fun generate(
        @Header("X-API-Token") gateway: String,
        @Header("Authorization") bearer: String
    ): RecommendResponse

    @GET("agent/recommend/history")
    suspend fun history(
        @Header("X-API-Token") gateway: String,
        @Header("Authorization") bearer: String,
        @Query("limit") limit: Int = 10
    ): List<HistoryItem>
}
```

---

## 4. UI Suggestions (non-binding)

The `evidence` array is the differentiator of this feature — it lets the user see _why_ the AI gave this advice. Suggested layouts:

**Main screen:**

- Big "Generate Today's Recommendation" button
- Loading spinner during the 5–15s wait
- Result card showing `recommendation` text prominently
- Expandable "See reasoning" section listing `evidence[].summary` entries
- "View history" link to the second screen

**History screen:**

- Vertical list of past recommendations, newest first
- Each item: date + first 2 lines of content + tap-to-expand for full content & evidence

These are starting points, not a spec. Any UI ideas welcome.

---

## 5. Testing

Local server runs at `http://152.42.181.66:8000` once the PR is merged and deployed by wushi2333.

For local debugging during development you can hit the endpoint with curl:

```bash
# 1. Get a JWT
TOKEN=$(curl -X POST http://152.42.181.66:8000/login \
  -H "X-API-Token: team-wellness-2025" \
  -H "Content-Type: application/json" \
  -d '{"username":"testuser","password":"testpass"}' | jq -r .access_token)

# 2. Call the agent
curl -X POST http://152.42.181.66:8000/agent/recommend \
  -H "X-API-Token: team-wellness-2025" \
  -H "Authorization: Bearer $TOKEN"
```

An end-to-end smoke test script is committed at `Service_Backend/tests/smoke_test.sh` covering register → login → seed → recommend → history.

---

## 6. Notes for reviewers

- The new table `agent_recommendations` is auto-created by `Base.metadata.create_all(...)` on first server start after merge. No manual SQL required.
- The agent reuses the existing DeepSeek API key (`DEEPSEEK_API_KEY` env var) — no new secrets introduced.
- Modifications to `main.py` are limited to one import line near the top and four lines appended at the end (router include). No existing code is touched.
