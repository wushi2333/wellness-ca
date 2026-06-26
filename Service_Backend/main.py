# Author Xia Zihang
import json
import os
from fastapi import FastAPI, Depends, HTTPException, Header, status
from pydantic import BaseModel
from sqlalchemy.orm import Session
from sqlalchemy import Column, Integer, String, Float, Date, Text, DateTime, ForeignKey
from sqlalchemy.sql import func
from openai import OpenAI
import database
import security

app = FastAPI(title="Wellness CA Backend", version="1.0")

# ---------------------------------------------------------------------------
# SQLAlchemy Models
# ---------------------------------------------------------------------------
class User(database.Base):
    __tablename__ = "users"
    id = Column(Integer, primary_key=True, index=True)
    username = Column(String(50), unique=True, nullable=False)
    hashed_password = Column(String(255), nullable=False)


class WellnessRecord(database.Base):
    __tablename__ = "wellness_records"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    sleep_hours = Column(Float, nullable=False)
    exercise_activity = Column(String(100))
    exercise_duration = Column(Integer)
    record_date = Column(Date, nullable=False)
    notes = Column(Text)
    created_at = Column(DateTime, server_default=func.now())


class ChatHistory(database.Base):
    __tablename__ = "chat_history"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    question = Column(Text, nullable=False)
    answer = Column(Text, nullable=False)
    created_at = Column(DateTime, server_default=func.now())


class Recommendation(database.Base):
    __tablename__ = "recommendations"
    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False)
    content = Column(Text, nullable=False)
    created_at = Column(DateTime, server_default=func.now())


# Auto-create tables (safe — uses IF NOT EXISTS via SQLAlchemy)
database.Base.metadata.create_all(bind=database.engine)

# ---------------------------------------------------------------------------
# DeepSeek client (Key from server .env — never leaves this machine)
# ---------------------------------------------------------------------------
_deepseek = OpenAI(
    base_url="https://api.deepseek.com",
    api_key=os.getenv("DEEPSEEK_API_KEY"),
)

# ---------------------------------------------------------------------------
# Pydantic Schemas
# ---------------------------------------------------------------------------
class UserAuthSchema(BaseModel):
    username: str
    password: str


class WellnessEntry(BaseModel):
    sleep_hours: float
    exercise_activity: str = ""
    exercise_duration: int = 0
    record_date: str          # "YYYY-MM-DD"
    notes: str = ""


class ChatRequest(BaseModel):
    message: str


# ---------------------------------------------------------------------------
# API Gateway guard
# ---------------------------------------------------------------------------
_GATEWAY_TOKEN = os.getenv("API_GATEWAY_TOKEN", "team-wellness-2025")


def verify_gateway(x_api_token: str = Header(None)):
    if x_api_token != _GATEWAY_TOKEN:
        raise HTTPException(status.HTTP_403_FORBIDDEN, detail="Forbidden")


# ---------------------------------------------------------------------------
# Auth dependency (JWT)
# ---------------------------------------------------------------------------
def current_user_id(authorization: str = Header(None)) -> int:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Missing token")
    payload = security.verify_access_token(authorization.split(" ")[1])
    if not payload or not payload.get("sub"):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
    return int(payload["sub"])


# ===========================  AUTH  =========================================
@app.post("/register", status_code=status.HTTP_201_CREATED)
def register(payload: UserAuthSchema, _=Depends(verify_gateway),
             db: Session = Depends(database.get_db)):
    if db.query(User).filter(User.username == payload.username).first():
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Username exists")
    user = User(username=payload.username,
                hashed_password=security.get_password_hash(payload.password))
    db.add(user)
    db.commit()
    db.refresh(user)
    return {"message": "Registered", "user_id": user.id}


@app.post("/login")
def login(payload: UserAuthSchema, _=Depends(verify_gateway),
          db: Session = Depends(database.get_db)):
    user = db.query(User).filter(User.username == payload.username).first()
    if not user or not security.verify_password(payload.password, user.hashed_password):
        raise HTTPException(status.HTTP_400_BAD_REQUEST, detail="Bad credentials")
    token = security.create_access_token({"sub": str(user.id)})
    return {"access_token": token, "token_type": "bearer"}


# =========================  WELLNESS CRUD  ==================================
@app.get("/records")
def list_records(_=Depends(verify_gateway), user_id: int = Depends(current_user_id),
                 db: Session = Depends(database.get_db)):
    rows = (db.query(WellnessRecord)
            .filter(WellnessRecord.user_id == user_id)
            .order_by(WellnessRecord.record_date.desc())
            .all())
    return [{"id": r.id, "sleep_hours": r.sleep_hours,
             "exercise_activity": r.exercise_activity,
             "exercise_duration": r.exercise_duration,
             "record_date": str(r.record_date), "notes": r.notes} for r in rows]


@app.post("/records", status_code=status.HTTP_201_CREATED)
def create_record(entry: WellnessEntry, _=Depends(verify_gateway),
                  user_id: int = Depends(current_user_id),
                  db: Session = Depends(database.get_db)):
    rec = WellnessRecord(user_id=user_id, **entry.model_dump())
    db.add(rec)
    db.commit()
    db.refresh(rec)
    return {"message": "Created", "id": rec.id}


@app.put("/records/{record_id}")
def update_record(record_id: int, entry: WellnessEntry,
                  _=Depends(verify_gateway),
                  user_id: int = Depends(current_user_id),
                  db: Session = Depends(database.get_db)):
    rec = db.query(WellnessRecord).filter(
        WellnessRecord.id == record_id, WellnessRecord.user_id == user_id).first()
    if not rec:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Record not found")
    for k, v in entry.model_dump().items():
        setattr(rec, k, v)
    db.commit()
    return {"message": "Updated"}


@app.delete("/records/{record_id}")
def delete_record(record_id: int, _=Depends(verify_gateway),
                  user_id: int = Depends(current_user_id),
                  db: Session = Depends(database.get_db)):
    rec = db.query(WellnessRecord).filter(
        WellnessRecord.id == record_id, WellnessRecord.user_id == user_id).first()
    if not rec:
        raise HTTPException(status.HTTP_404_NOT_FOUND, detail="Record not found")
    db.delete(rec)
    db.commit()
    return {"message": "Deleted"}


# ===========================  CHATBOT  ======================================
SYSTEM_PROMPT = (
    "You are a friendly wellness assistant. "
    "Give concise, practical health advice. "
    "Ask clarifying questions when needed."
)

@app.post("/chat")
def chat(req: ChatRequest, _=Depends(verify_gateway),
         user_id: int = Depends(current_user_id),
         db: Session = Depends(database.get_db)):
    resp = _deepseek.chat.completions.create(
        model="deepseek-chat",
        messages=[{"role": "system", "content": SYSTEM_PROMPT},
                  {"role": "user", "content": req.message}],
        temperature=0.7, max_tokens=512,
    )
    reply = resp.choices[0].message.content
    db.add(ChatHistory(user_id=user_id, question=req.message, answer=reply))
    db.commit()
    return {"reply": reply}


# ========================  AGENTIC AI  ======================================
AGENTIC_PROMPT = (
    "You are a wellness coach. Below is a user's recent health data. "
    "Analyze trends and give 3 short, personalized, actionable tips. "
    "Return ONLY a JSON array of strings: [\"tip 1\", \"tip 2\", \"tip 3\"]"
)

@app.get("/recommendations")
def get_recommendations(_=Depends(verify_gateway),
                        user_id: int = Depends(current_user_id),
                        db: Session = Depends(database.get_db)):
    records = (db.query(WellnessRecord)
               .filter(WellnessRecord.user_id == user_id)
               .order_by(WellnessRecord.record_date.desc())
               .limit(7).all())
    if not records:
        return {"recommendations": ["Start logging your wellness data to get tips!"]}

    summary = "\n".join(
        f"- {r.record_date}: sleep {r.sleep_hours}h, "
        f"{r.exercise_activity or 'no exercise'} ({r.exercise_duration} min)"
        for r in records
    )
    resp = _deepseek.chat.completions.create(
        model="deepseek-chat",
        messages=[{"role": "system", "content": AGENTIC_PROMPT},
                  {"role": "user", "content": f"Recent data:\n{summary}"}],
        temperature=0.8, max_tokens=400,
    )
    raw = resp.choices[0].message.content
    try:
        tips = json.loads(raw)
    except json.JSONDecodeError:
        tips = [raw]

    for tip in tips:
        db.add(Recommendation(user_id=user_id, content=tip))
    db.commit()
    return {"recommendations": tips}
