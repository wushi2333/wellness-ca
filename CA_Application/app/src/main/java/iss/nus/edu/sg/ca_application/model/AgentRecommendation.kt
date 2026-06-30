package iss.nus.edu.sg.ca_application.model

/**
 * API Contract: POST /agent/recommend (response) and
 *               GET  /agent/recommend/history (list item)
 *
 * Response from the agentic AI sidecar (proxied through Spring Boot).
 *
 * POST /agent/recommend response:
 * {
 *   "recommendation": "Your sleep has declined ...",
 *   "evidence": [
 *     { "name": "get_recent_wellness_records", "summary": "Loaded 7 records over the last 7 days" },
 *     { "name": "analyze_sleep_pattern", "summary": "Mean 6.0h, trend -0.50h/day, 3 nights below 6.0h" },
 *     { "name": "analyze_exercise_pattern", "summary": "Total 90 min across 4 days, gap to WHO guideline: 60.0 min" },
 *     { "name": "save_recommendation", "summary": "Persisted recommendation id=42" }
 *   ],
 *   "iterations": 3,
 *   "savedId": 42
 * }
 *
 * GET /agent/recommend/history?limit=10 response:
 *   List of items with shape:
 *   {
 *     "id": 42,
 *     "content": "...",
 *     "evidence": [...],
 *     "iterations": 3,
 *     "createdAt": "2026-06-30T14:22:31"
 *   }
 *
 * Required headers (same as all backend calls):
 *   Authorization: Bearer <accessToken>
 *   X-API-Token:   team-wellness-2025
 *
 * Latency: 5-15 seconds. UI must show a loading indicator.
 *
 * Author: Cai Peilin
 */

data class ToolTrace(
    val name: String,
    val summary: String
)

data class AgentRecommendation(
    val recommendation: String,
    val evidence: List<ToolTrace>,
    val iterations: Int,
    val savedId: Int
)

data class AgentHistoryItem(
    val id: Int,
    val content: String,
    val evidence: List<ToolTrace>,
    val iterations: Int,
    val createdAt: String
)