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
    """Daily journal — one row per user per date. Links to sleep / exercise tables."""
    __tablename__ = "wellness_records"
    id = Column(Integer, primary_key=True)
    user_id = Column(Integer)
    record_date = Column(Date)
    sleep_record_id = Column(Integer, nullable=True)


class SleepLog(Base):
    __tablename__ = "sleep_records"
    id = Column(Integer, primary_key=True)
    sleep_hours = Column(Float)
    sleep_time = Column(String(5))
    wake_time = Column(String(5))
    mood_score = Column(Integer)
    notes = Column(Text)


class ExerciseLog(Base):
    __tablename__ = "exercise_records"
    id = Column(Integer, primary_key=True)
    daily_record_id = Column(Integer)
    exercise_activity = Column(String(100))
    exercise_duration = Column(Integer)
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

Data format note: The database may contain MULTIPLE rows for the same date — one row carrying sleep data (sleep_hours > 0, exercise fields zero), and separate rows for each exercise activity (exercise_duration > 0, sleep_hours = 0). The analysis tools already handle this correctly by filtering on the relevant field. You do not need to deduplicate.

Hard rules:
- Never invent numbers. Every claim about the user must come from a tool result.
- If the user has fewer than 3 sleep records, advise them to log more sleep data instead of giving statistics-based advice.
- Keep the final recommendation under 80 words.
"""

TOOL_SPECS_EN = [
    {"type": "function", "function": {"name": "get_recent_wellness_records", "description": "Retrieve wellness records for the past N days. Call first.", "parameters": {"type": "object", "properties": {"days": {"type": "integer", "description": "Days back (1-30)", "minimum": 1, "maximum": 30}}, "required": ["days"]}}},
    {"type": "function", "function": {"name": "analyze_sleep_pattern", "description": "Compute sleep stats: mean, std, trend, short-sleep nights.", "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "analyze_exercise_pattern", "description": "Compute exercise stats: total min, active days, gap to WHO 150min.", "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "save_recommendation", "description": "Persist final recommendation. Call once as last action.", "parameters": {"type": "object", "properties": {"content": {"type": "string", "description": "Recommendation text"}}, "required": ["content"]}}},
]

TOOL_SPECS_ZH = [
    {"type": "function", "function": {"name": "get_recent_wellness_records", "description": "获取最近N天的健康记录。请先调用此函数。", "parameters": {"type": "object", "properties": {"days": {"type": "integer", "description": "查询天数（1-30）", "minimum": 1, "maximum": 30}}, "required": ["days"]}}},
    {"type": "function", "function": {"name": "analyze_sleep_pattern", "description": "分析睡眠模式：均值、标准差、趋势、睡眠不足的夜晚数。", "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "analyze_exercise_pattern", "description": "分析运动模式：总时间、活跃天数、距离WHO 150分钟目标的差距。", "parameters": {"type": "object", "properties": {}}}},
    {"type": "function", "function": {"name": "save_recommendation", "description": "保存最终建议。作为最后一步操作，只需调用一次。", "parameters": {"type": "object", "properties": {"content": {"type": "string", "description": "建议文本"}}, "required": ["content"]}}},
]

ZH_SYSTEM_PROMPT = """你是一个健康建议助手。

你的任务：基于用户最近的实际数据，生成一条可执行的个性化健康建议。

工作流程：
1. 调用 get_recent_wellness_records（使用 days=7）加载数据。
2. 调用 analyze_sleep_pattern 和 analyze_exercise_pattern 提取趋势。
3. 撰写一条简洁建议（2-4句话），引用分析中的具体数字。不要给出泛泛而谈的通用建议。
4. 调用 save_recommendation 保存最终文本。这必须是你的最后一步操作。

数据格式说明：数据库中同一日期可能有多行数据——一行为睡眠数据（sleep_hours > 0，运动字段为零），其他行为各自的运动记录（exercise_duration > 0，sleep_hours = 0）。分析工具已正确处理此格式，你无需手动去重。

硬性规则：
- 绝不编造数字。关于用户的任何说法都必须来自工具返回的结果。
- 如果用户的睡眠记录不足3条，建议他们记录更多睡眠数据，而不是给出基于统计数据的建议。
- 最终建议保持在80字以内。
- 必须用中文回复。
"""


# Lightweight record object for analysis (flattened from 3-table schema).
class SimpleRecord:
    def __init__(self, date, sleep_hours, sleep_time, wake_time, mood_score, notes,
                 exercise_duration, exercise_activity):
        self.record_date = date
        self.sleep_hours = sleep_hours
        self.sleep_time = sleep_time
        self.wake_time = wake_time
        self.mood_score = mood_score
        self.notes = notes
        self.exercise_duration = exercise_duration
        self.exercise_activity = exercise_activity


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
        # Query daily journals
        dailies = (self._db.query(WellnessRecord)
                   .filter(WellnessRecord.user_id == self._user_id,
                           WellnessRecord.record_date >= cutoff)
                   .order_by(WellnessRecord.record_date.asc()).all())

        # Batch-load sleep records
        sleep_ids = [d.sleep_record_id for d in dailies if d.sleep_record_id]
        sleep_map = {}
        if sleep_ids:
            for s in self._db.query(SleepLog).filter(SleepLog.id.in_(sleep_ids)).all():
                sleep_map[s.id] = s

        # Batch-load exercise records
        daily_ids = [d.id for d in dailies]
        ex_map = {}
        if daily_ids:
            for e in self._db.query(ExerciseLog).filter(ExerciseLog.daily_record_id.in_(daily_ids)).all():
                ex_map.setdefault(e.daily_record_id, []).append(e)

        # Build flat record list for analysis functions
        flat_records = []
        for d in dailies:
            s = sleep_map.get(d.sleep_record_id)
            ex_list = ex_map.get(d.id, [])
            if s:
                flat_records.append(SimpleRecord(
                    date=str(d.record_date), sleep_hours=s.sleep_hours or 0,
                    sleep_time=s.sleep_time or "", wake_time=s.wake_time or "",
                    mood_score=s.mood_score or 0, notes=s.notes or "",
                    exercise_duration=0, exercise_activity=""))
            for e in ex_list:
                flat_records.append(SimpleRecord(
                    date=str(d.record_date), sleep_hours=0,
                    sleep_time="", wake_time="", mood_score=0, notes=e.notes or "",
                    exercise_duration=e.exercise_duration or 0,
                    exercise_activity=e.exercise_activity or ""))
        self._records = flat_records

        # Build JSON-serializable record list
        record_list = []
        for d in dailies:
            rec = {"date": str(d.record_date)}
            s = sleep_map.get(d.sleep_record_id)
            if s:
                rec["sleep"] = {"sleep_hours": s.sleep_hours or 0,
                                "sleep_time": s.sleep_time or "",
                                "wake_time": s.wake_time or "",
                                "mood_score": s.mood_score or 0,
                                "notes": s.notes or ""}
            ex_list = ex_map.get(d.id, [])
            rec["exercises"] = [{"activity": e.exercise_activity or "",
                                 "duration": e.exercise_duration or 0,
                                 "notes": e.notes or ""} for e in ex_list]
            record_list.append(rec)

        unique_dates = len(dailies)
        sleep_rows = len(sleep_ids)
        ex_rows = sum(len(v) for v in ex_map.values())
        result = {"count": len(flat_records), "unique_dates": unique_dates,
                  "sleep_entries": sleep_rows, "exercise_entries": ex_rows,
                  "days_requested": days, "records": record_list}
        self._log("get_recent_wellness_records",
                  f"Loaded {unique_dates} dates ({sleep_rows} sleep, {ex_rows} exercise)")
        return result

    def analyze_sleep_pattern(self) -> dict:
        if not self._records: return {"error": "Call get_recent_wellness_records first."}
        # Only consider records that actually carry sleep data (exercise-only rows have sleep_hours=0 or None)
        sleep_records = [r for r in self._records if r.sleep_hours and r.sleep_hours > 0]
        if not sleep_records:
            self._log("analyze_sleep_pattern", "No sleep records found")
            return {"n": 0, "note": "No sleep data in this period."}
        hours = [r.sleep_hours for r in sleep_records]
        mean = sum(hours) / len(hours)
        std = _std(hours); slope = _slope(hours)
        short = sum(1 for h in hours if h < SHORT_SLEEP_HOURS)
        self._log("analyze_sleep_pattern", f"Mean {mean:.1f}h (n={len(hours)} nights), trend {slope:+.2f}h/day, {short} nights <{SHORT_SLEEP_HOURS}h")
        return {"n": len(hours), "mean_hours": round(mean, 2), "std_hours": round(std, 2),
                "trend_per_day": round(slope, 3), "short_sleep_nights": short}

    def analyze_exercise_pattern(self) -> dict:
        if not self._records: return {"error": "Call get_recent_wellness_records first."}
        # Exercise records: rows that actually carry exercise data
        ex_records = [r for r in self._records if r.exercise_duration and r.exercise_duration > 0]
        durations = [r.exercise_duration for r in ex_records]
        activities = [r.exercise_activity for r in ex_records if r.exercise_activity]
        total = sum(durations)
        # Count unique dates (multiple exercises on the same day = 1 active day)
        active_days = len({str(r.record_date) for r in ex_records})
        top = Counter(activities).most_common(1)[0][0] if activities else None
        # Window: count unique dates across ALL records as the observation window
        window_days = len({str(r.record_date) for r in self._records})
        gap = round(WHO_WEEKLY_MIN * max(window_days, 1) / 7 - total, 1)
        self._log("analyze_exercise_pattern", f"Total {total} min, {active_days} active days, top activity: {top}, gap {gap} min")
        return {"total_minutes": total, "active_days": active_days,
                "most_common_activity": top, "gap_to_who_guideline_min": gap,
                "sessions": len(ex_records)}

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

def run_agent(db, user_id: int, language: str = "en") -> dict:
    client = _build_client()
    tk = AgentToolkit(db=db, user_id=user_id)
    logger.info(f"run_agent called with language={language}")
    is_zh = (language == "zh")
    prompt = ZH_SYSTEM_PROMPT if is_zh else SYSTEM_PROMPT
    tools = TOOL_SPECS_ZH if is_zh else TOOL_SPECS_EN
    user_msg = "请为我生成今天的健康建议。" if is_zh else "Please generate today's wellness recommendation for me."
    msgs = [{"role": "system", "content": prompt},
            {"role": "user", "content": user_msg}]
    saved_id = None

    for step in range(1, MAX_ITERATIONS + 1):
        resp = client.chat.completions.create(model="deepseek-chat", messages=msgs, tools=tools, tool_choice="auto", temperature=0.3)
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
