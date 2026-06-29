# Author: Cai Peilin
"""Agentic wellness recommendation core — models, tools, executor loop."""

import json, os, logging
from collections import Counter
from datetime import date, timedelta

from openai import OpenAI
from sqlalchemy import Column, Integer, String, Float, Date, Text, JSON, DateTime, ForeignKey, create_engine
from sqlalchemy.orm import Session, sessionmaker, declarative_base
from sqlalchemy.sql import func
from dotenv import load_dotenv

load_dotenv()

logger = logging.getLogger(__name__)

# ---------------------------------------------------------------------------
# Database
# ---------------------------------------------------------------------------
_DB_USER = os.getenv("DB_USER")
_DB_PASS = os.getenv("DB_PASSWORD")
_DB_HOST = os.getenv("DB_HOST")
_DB_PORT = os.getenv("DB_PORT")
_DB_NAME = os.getenv("DB_NAME")
DB_URL = f"mysql+pymysql://{_DB_USER}:{_DB_PASS}@{_DB_HOST}:{_DB_PORT}/{_DB_NAME}"
_engine = create_engine(DB_URL, pool_pre_ping=True,
    connect_args={"ssl": {"ssl_ca": os.getenv("DB_SSL_CA", "")}} if os.getenv("DB_SSL_CA") else {})
_SessionLocal = sessionmaker(bind=_engine)
Base = declarative_base()


def get_db():
    db = _SessionLocal()
    try: yield db
    finally: db.close()


# ---------------------------------------------------------------------------
# Models (maps to existing tables managed by Spring Boot)
# ---------------------------------------------------------------------------
class WellnessRecord(Base):
    __tablename__ = "wellness_records"
    id = Column(Integer, primary_key=True)
    user_id = Column(Integer)
    sleep_hours = Column(Float)
    exercise_activity = Column(String(100))
    exercise_duration = Column(Integer)
    record_date = Column(Date)
    notes = Column(Text)


class AgentRecommendation(Base):
    __tablename__ = "agent_recommendations"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, nullable=False, index=True)
    content = Column(Text, nullable=False)
    evidence = Column(JSON, nullable=False)
    iterations = Column(Integer, nullable=False)
    created_at = Column(DateTime, server_default=func.now())


# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
WHO_WEEKLY_MIN = 150
SHORT_SLEEP_HOURS = 6.0
MAX_ITERATIONS = 5
STOP_AFTER_TOOL = "save_recommendation"

SYSTEM_PROMPT = """You are a wellness recommendation agent.

Your job: produce ONE actionable, personalised wellness recommendation for the user, grounded in their actual recent data.

Required workflow:
1. Call get_recent_wellness_records (use days=7) to load the data.
2. Call analyze_sleep_pattern and analyze_exercise_pattern to extract trends.
3. Write a single recommendation (2-4 sentences) that cites specific numbers from the analysis. Do not give generic advice.
4. Call save_recommendation with the final text. This must be your last action.

Hard rules:
- Never invent numbers. Every claim about the user must come from a tool result.
- If the user has fewer than 3 records, advise them to log more data instead of giving statistics-based advice.
- Keep the final recommendation under 80 words.
"""

TOOL_SPECS = [
    {"type": "function", "function": {"name": "get_recent_wellness_records", "description": "Retrieve wellness records for the past N days. Call first.", "parameters": {"type": "object", "properties": {"days": {"type": "integer", "description": "Days back (1-30)", "minimum": 1, "maximum": 30}}, "required": ["days"]}}},
    {"type": "function", "function": {"name": "analyze_sleep_pattern", "description": "Compute sleep stats: mean, std, trend, short-sleep nights.", "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "analyze_exercise_pattern", "description": "Compute exercise stats: total min, active days, gap to WHO 150min.", "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "save_recommendation", "description": "Persist final recommendation. Call once as last action.", "parameters": {"type": "object", "properties": {"content": {"type": "string", "description": "Recommendation text"}}, "required": ["content"]}}},
]


# ---------------------------------------------------------------------------
# Toolkit
# ---------------------------------------------------------------------------
class AgentToolkit:
    def __init__(self, db: Session, user_id: int):
        self._db, self._user_id = db, user_id
        self._records: list = None
        self.trace: list[dict] = []

    def get_recent_wellness_records(self, days: int) -> dict:
        cutoff = date.today() - timedelta(days=days)
        rows = (self._db.query(WellnessRecord)
                .filter(WellnessRecord.user_id == self._user_id,
                        WellnessRecord.record_date >= cutoff)
                .order_by(WellnessRecord.record_date.asc()).all())
        self._records = rows
        result = {"count": len(rows), "days_requested": days, "records": [
            {"date": str(r.record_date), "sleep_hours": r.sleep_hours,
             "exercise_activity": r.exercise_activity or "", "exercise_duration": r.exercise_duration or 0}
            for r in rows]}
        self._log("get_recent_wellness_records", f"Loaded {len(rows)} records over {days} days")
        return result

    def analyze_sleep_pattern(self) -> dict:
        if not self._records: return {"error": "Call get_recent_wellness_records first."}
        hours = [r.sleep_hours for r in self._records]
        mean = sum(hours) / len(hours)
        std = _std(hours); slope = _slope(hours)
        short = sum(1 for h in hours if h < SHORT_SLEEP_HOURS)
        self._log("analyze_sleep_pattern", f"Mean {mean:.1f}h, trend {slope:+.2f}h/day, {short} nights <{SHORT_SLEEP_HOURS}h")
        return {"n": len(hours), "mean_hours": round(mean, 2), "std_hours": round(std, 2),
                "trend_per_day": round(slope, 3), "short_sleep_nights": short}

    def analyze_exercise_pattern(self) -> dict:
        if not self._records: return {"error": "Call get_recent_wellness_records first."}
        durations = [r.exercise_duration or 0 for r in self._records]
        activities = [r.exercise_activity for r in self._records if r.exercise_activity]
        total = sum(durations); active_days = sum(1 for d in durations if d > 0)
        top = Counter(activities).most_common(1)[0][0] if activities else None
        window_days = len(self._records)
        gap = round(WHO_WEEKLY_MIN * window_days / 7 - total, 1)
        self._log("analyze_exercise_pattern", f"Total {total} min, {active_days} active days, gap {gap} min")
        return {"total_minutes": total, "active_days": active_days,
                "most_common_activity": top, "gap_to_who_guideline_min": gap}

    def save_recommendation(self, content: str) -> dict:
        rec = AgentRecommendation(user_id=self._user_id, content=content,
                                  evidence=self.trace, iterations=sum(1 for t in self.trace if t["name"] != STOP_AFTER_TOOL))
        self._db.add(rec); self._db.commit(); self._db.refresh(rec)
        self._log("save_recommendation", f"Persisted recommendation id={rec.id}")
        return {"saved_id": rec.id}

    def _log(self, name: str, summary: str):
        self.trace.append({"name": name, "summary": summary})


def _std(vals): n=len(vals); m=sum(vals)/n; return (sum((v-m)**2 for v in vals)/(n-1))**0.5 if n>1 else 0.0
def _slope(vals): n=len(vals); xs=list(range(n)); xm=sum(xs)/n; ym=sum(vals)/n; num=sum((x-xm)*(y-ym) for x,y in zip(xs,vals)); den=sum((x-xm)**2 for x in xs); return num/den if den else 0.0


# ---------------------------------------------------------------------------
# Executor loop
# ---------------------------------------------------------------------------
class AgentError(Exception): pass

def _build_client():
    return OpenAI(base_url="https://api.deepseek.com", api_key=os.getenv("DEEPSEEK_API_KEY"))

def run_agent(db, user_id: int) -> dict:
    client = _build_client()
    tk = AgentToolkit(db=db, user_id=user_id)
    msgs = [{"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": "Please generate today's wellness recommendation for me."}]
    saved_id = None

    for step in range(1, MAX_ITERATIONS + 1):
        resp = client.chat.completions.create(model="deepseek-chat", messages=msgs, tools=TOOL_SPECS, tool_choice="auto", temperature=0.3)
        msg = resp.choices[0].message
        if not msg.tool_calls:
            if saved_id is None: raise AgentError(f"Agent terminated at step {step} without saving.")
            break
        msgs.append({"role": "assistant", "content": msg.content or "", "tool_calls": [
            {"id": tc.id, "type": "function", "function": {"name": tc.function.name, "arguments": tc.function.arguments}} for tc in msg.tool_calls]})
        for tc in msg.tool_calls:
            args = json.loads(tc.function.arguments or "{}") if tc.function.arguments else {}
            try: result = getattr(tk, tc.function.name)(**args)
            except Exception as e: result = {"error": str(e)}
            if tc.function.name == STOP_AFTER_TOOL and "saved_id" in result: saved_id = result["saved_id"]
            msgs.append({"role": "tool", "tool_call_id": tc.id, "name": tc.function.name, "content": json.dumps(result, default=str)})
        if saved_id is not None: break
    else: raise AgentError(f"Agent exceeded {MAX_ITERATIONS} iterations without saving.")

    content = ""
    for m in reversed(msgs):
        if m.get("role") != "assistant": continue
        for tc in m.get("tool_calls", []) or []:
            if tc["function"]["name"] == STOP_AFTER_TOOL:
                try: content = json.loads(tc["function"]["arguments"]).get("content", "")
                except: pass
    iterations = sum(1 for t in tk.trace if t["name"] != STOP_AFTER_TOOL)
    return {"recommendation": content, "evidence": tk.trace, "iterations": iterations, "saved_id": saved_id}
