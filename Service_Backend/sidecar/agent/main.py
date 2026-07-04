# Author: Cai Peilin
"""Agentic recommendation sidecar — HTTP API for Spring Boot backend."""
import os
from fastapi import FastAPI, Depends, Header, HTTPException, status
from sqlalchemy.orm import Session
from pydantic import BaseModel
from jose import jwt, JWTError
from typing import Optional
import agent_core

class RecommendReq(BaseModel):
    language: Optional[str] = "en"
    user_id: Optional[int] = None  # passed by Spring Boot, not used for auth

app = FastAPI(title="Wellness Agent Sidecar", version="1.0")

# Auth
_GATEWAY = os.getenv("API_GATEWAY_TOKEN", "team-wellness-2025")
_SECRET = os.getenv("JWT_SECRET_KEY")
_ALGO = os.getenv("JWT_ALGORITHM", "HS512")

def _gw(x_api_token: str = Header(None)):
    if x_api_token != _GATEWAY: raise HTTPException(403)

def _uid(authorization: str = Header(None)) -> int:
    if not authorization or not authorization.startswith("Bearer "): raise HTTPException(401)
    try: p = jwt.decode(authorization[7:], _SECRET, algorithms=["HS256","HS512"])
    except JWTError: raise HTTPException(401)
    if not p.get("sub"): raise HTTPException(401)
    return int(p["sub"])


@app.post("/recommend")
def recommend(body: RecommendReq, _=Depends(_gw),
              user_id: int = Depends(_uid),
              db: Session = Depends(agent_core.get_db)):
    lang = body.language if body.language in ("zh", "en") else "en"
    print(f"[recommend] body.language={body.language} -> lang={lang}", flush=True)
    try: return agent_core.run_agent(db=db, user_id=user_id, language=lang)
    except agent_core.AgentError as e: raise HTTPException(502, detail=str(e))


@app.get("/recommend/history")
def history(_=Depends(_gw), user_id: int = Depends(_uid),
            db: Session = Depends(agent_core.get_db), limit: int = 10):
    limit = max(1, min(limit, 50))
    rows = (db.query(agent_core.AgentRecommendation)
            .filter(agent_core.AgentRecommendation.user_id == user_id)
            .order_by(agent_core.AgentRecommendation.created_at.desc())
            .limit(limit).all())
    return [{"id": r.id, "content": r.content, "evidence": r.evidence,
             "iterations": r.iterations, "created_at": str(r.created_at)} for r in rows]


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8002, reload=False)
