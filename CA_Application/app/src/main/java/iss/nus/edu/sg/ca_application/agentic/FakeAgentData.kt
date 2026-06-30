package iss.nus.edu.sg.ca_application.agentic

import iss.nus.edu.sg.ca_application.model.AgentHistoryItem
import iss.nus.edu.sg.ca_application.model.AgentRecommendation
import iss.nus.edu.sg.ca_application.model.ToolTrace

/**
 * Static fake data for offline UI development.
 *
 * Used while the Java backend is not yet reachable. Replaced by real
 * HTTP calls in Phase 8.3.5.D / Phase 8.3.6 backend integration.
 *
 * Author: Cai Peilin
 */
object FakeAgentData {

    fun freshRecommendation() = AgentRecommendation(
        recommendation = "Your sleep has declined sharply — down 0.5 hours per night " +
                "over the past week, averaging just 6.0 hours with 3 nights below 6 " +
                "hours. Meanwhile, you're 60 minutes short of the WHO 150-minute " +
                "weekly target. Try winding down 30 minutes earlier tonight, and aim " +
                "for a 30-minute walk tomorrow.",
        evidence = listOf(
            ToolTrace("get_recent_wellness_records", "Loaded 7 records over the last 7 days"),
            ToolTrace("analyze_sleep_pattern", "Mean 6.0h, trend -0.50h/day, 3 nights below 6.0h"),
            ToolTrace("analyze_exercise_pattern", "Total 90 min across 4 days, gap to WHO guideline: 60.0 min"),
            ToolTrace("save_recommendation", "Persisted recommendation id=42")
        ),
        iterations = 3,
        savedId = 42
    )

    fun history(): List<AgentHistoryItem> = listOf(
        AgentHistoryItem(
            id = 42,
            content = "Your sleep has declined sharply — down 0.5 hours per night over the past week, " +
                    "averaging just 6.0 hours with 3 nights below 6 hours.",
            evidence = listOf(
                ToolTrace("get_recent_wellness_records", "Loaded 7 records over the last 7 days"),
                ToolTrace("analyze_sleep_pattern", "Mean 6.0h, trend -0.50h/day"),
                ToolTrace("analyze_exercise_pattern", "Total 90 min, 60 min short"),
                ToolTrace("save_recommendation", "Persisted recommendation id=42")
            ),
            iterations = 3,
            createdAt = "2026-06-30T14:22:31"
        ),
        AgentHistoryItem(
            id = 41,
            content = "Sleep is stable at 7.2h on average. Exercise is on track at 165 minutes this week. " +
                    "Keep the current pattern; consider adding one strength session.",
            evidence = listOf(
                ToolTrace("get_recent_wellness_records", "Loaded 7 records over the last 7 days"),
                ToolTrace("analyze_sleep_pattern", "Mean 7.2h, trend +0.05h/day"),
                ToolTrace("analyze_exercise_pattern", "Total 165 min, exceeds WHO guideline"),
                ToolTrace("save_recommendation", "Persisted recommendation id=41")
            ),
            iterations = 3,
            createdAt = "2026-06-23T08:15:04"
        ),
        AgentHistoryItem(
            id = 40,
            content = "Limited data this week (only 2 records). Log at least 3 more days before requesting " +
                    "a trend analysis.",
            evidence = listOf(
                ToolTrace("get_recent_wellness_records", "Loaded 2 records over the last 7 days"),
                ToolTrace("save_recommendation", "Persisted recommendation id=40")
            ),
            iterations = 2,
            createdAt = "2026-06-16T19:42:11"
        )
    )
}