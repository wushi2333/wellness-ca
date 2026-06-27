"""Tools available to the wellness recommendation agent.

Each public method on AgentToolkit corresponds to a function-call spec
in tool_specs.py. The agent loop dispatches by method name, so adding
a new tool means adding a method here and a spec there.

The toolkit caches loaded records and accumulates a tool trace, so
save_recommendation can persist the full reasoning chain alongside
the final text.

Author: Cai Peilin
"""
from collections import Counter
from datetime import date, timedelta

from sqlalchemy.orm import Session

from agent.models import AgentRecommendation


# WHO physical-activity guideline: 150 min moderate activity per week.
WHO_WEEKLY_MIN = 150
# Sleep Foundation threshold for short sleep in adults.
SHORT_SLEEP_HOURS = 6.0


class AgentToolkit:
    """Stateful container for the four tools the agent can call.

    Holds a per-request DB session, the resolved user_id, and a running
    trace of (name, summary) entries. The trace is persisted by
    save_recommendation so the mobile UI can show the user the reasoning
    that produced the advice.
    """

    def __init__(self, db: Session, user_id: int):
        self._db = db
        self._user_id = user_id
        self._records: list | None = None
        self.trace: list[dict] = []

    # ------------------------------------------------------------------
    # Tool 1 — retrieval
    # ------------------------------------------------------------------
    def get_recent_wellness_records(self, days: int) -> dict:
        # Deferred import: main.py imports this package at startup, so a
        # top-level `from main import WellnessRecord` would cycle.
        from main import WellnessRecord

        cutoff = date.today() - timedelta(days=days)
        rows = (
            self._db.query(WellnessRecord)
            .filter(WellnessRecord.user_id == self._user_id)
            .filter(WellnessRecord.record_date >= cutoff)
            .order_by(WellnessRecord.record_date.asc())
            .all()
        )
        self._records = rows
        result = {
            "count": len(rows),
            "days_requested": days,
            "records": [
                {
                    "date": str(r.record_date),
                    "sleep_hours": r.sleep_hours,
                    "exercise_activity": r.exercise_activity or "",
                    "exercise_duration": r.exercise_duration or 0,
                }
                for r in rows
            ],
        }
        self._log(
            "get_recent_wellness_records",
            f"Loaded {len(rows)} records over the last {days} days",
        )
        return result

    # ------------------------------------------------------------------
    # Tool 2 — sleep analysis
    # ------------------------------------------------------------------
    def analyze_sleep_pattern(self) -> dict:
        if not self._records:
            return self._records_not_loaded()

        hours = [r.sleep_hours for r in self._records]
        mean = sum(hours) / len(hours)
        std = _std(hours)
        slope = _slope(hours)
        short_nights = sum(1 for h in hours if h < SHORT_SLEEP_HOURS)

        result = {
            "n": len(hours),
            "mean_hours": round(mean, 2),
            "std_hours": round(std, 2),
            # +0.2 means sleep is improving by ~12 min per day across the window.
            "trend_per_day": round(slope, 3),
            "short_sleep_nights": short_nights,
        }
        self._log(
            "analyze_sleep_pattern",
            f"Mean {mean:.1f}h, trend {slope:+.2f}h/day, "
            f"{short_nights} nights below {SHORT_SLEEP_HOURS}h",
        )
        return result

    # ------------------------------------------------------------------
    # Tool 3 — exercise analysis
    # ------------------------------------------------------------------
    def analyze_exercise_pattern(self) -> dict:
        if not self._records:
            return self._records_not_loaded()

        durations = [r.exercise_duration or 0 for r in self._records]
        activities = [r.exercise_activity for r in self._records if r.exercise_activity]
        total = sum(durations)
        active_days = sum(1 for d in durations if d > 0)
        top_activity = Counter(activities).most_common(1)[0][0] if activities else None
        # Scale the WHO weekly guideline to the actual window length, so
        # a user with 3 days of data is not judged against a full week.
        window_days = len(self._records)
        scaled_guideline = WHO_WEEKLY_MIN * window_days / 7

        result = {
            "total_minutes": total,
            "active_days": active_days,
            "most_common_activity": top_activity,
            "gap_to_who_guideline_min": round(scaled_guideline - total, 1),
        }
        self._log(
            "analyze_exercise_pattern",
            f"Total {total} min across {active_days} days, "
            f"gap to WHO guideline: {result['gap_to_who_guideline_min']} min",
        )
        return result

    # ------------------------------------------------------------------
    # Tool 4 — persistence
    # ------------------------------------------------------------------
    def save_recommendation(self, content: str) -> dict:
        # Iteration count = tool calls before this final save.
        iterations = sum(1 for t in self.trace if t["name"] != "save_recommendation")

        rec = AgentRecommendation(
            user_id=self._user_id,
            content=content,
            evidence=self.trace,
            iterations=iterations,
        )
        self._db.add(rec)
        self._db.commit()
        self._db.refresh(rec)
        self._log("save_recommendation", f"Persisted recommendation id={rec.id}")
        return {"saved_id": rec.id, "created_at": str(rec.created_at)}

    # ------------------------------------------------------------------
    # Internal helpers
    # ------------------------------------------------------------------
    def _log(self, name: str, summary: str) -> None:
        self.trace.append({"name": name, "summary": summary})

    @staticmethod
    def _records_not_loaded() -> dict:
        return {
            "error": "Records have not been loaded. "
            "Call get_recent_wellness_records first."
        }


# ---------------------------------------------------------------------------
# Statistics helpers (pure Python — n is small, no need for numpy)
# ---------------------------------------------------------------------------
def _std(values: list[float]) -> float:
    n = len(values)
    if n < 2:
        return 0.0
    mean = sum(values) / n
    var = sum((v - mean) ** 2 for v in values) / (n - 1)
    return var ** 0.5


def _slope(values: list[float]) -> float:
    """Slope of values vs index using least-squares regression.

    Used to detect whether sleep hours are trending up or down across
    the window. Returns 0 when there are fewer than two points or all
    x values are identical.
    """
    n = len(values)
    if n < 2:
        return 0.0
    xs = list(range(n))
    x_mean = sum(xs) / n
    y_mean = sum(values) / n
    num = sum((x - x_mean) * (y - y_mean) for x, y in zip(xs, values))
    den = sum((x - x_mean) ** 2 for x in xs)
    return num / den if den else 0.0