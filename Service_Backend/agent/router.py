"""FastAPI router exposing the agentic recommendation endpoints.

Two endpoints:
- POST /agent/recommend           triggers a fresh agent run
- GET  /agent/recommend/history   returns the user's past agent-generated recs

Auth dependencies are inlined here (rather than imported from main) to
keep this router decoupled from main.py — the router can be moved to a
separate microservice later without touching the teammate's file.

Author: Cai Peilin
"""
import os

from fastapi import APIRouter, Depends, Header, HTTPException, status
from sqlalchemy.orm import Session

import database
import security
from agent.executor import run_agent, AgentError
from agent.models import AgentRecommendation
from agent.schemas import RecommendResponse, HistoryItem


router = APIRouter(prefix="/agent", tags=["agent"])


# ---------------------------------------------------------------------------
# Local auth dependencies. Mirrors main.py byte-for-byte so the contract is
# identical; duplication is intentional to keep this module independent.
# ---------------------------------------------------------------------------
def _verify_gateway(x_api_token: str = Header(None)):
    expected = os.getenv("API_GATEWAY_TOKEN", "team-wellness-2025")
    if x_api_token != expected:
        raise HTTPException(status.HTTP_403_FORBIDDEN, detail="Forbidden")


def _current_user_id(authorization: str = Header(None)) -> int:
    if not authorization or not authorization.startswith("Bearer "):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Missing token")
    payload = security.verify_access_token(authorization.split(" ")[1])
    if not payload or not payload.get("sub"):
        raise HTTPException(status.HTTP_401_UNAUTHORIZED, detail="Invalid token")
    return int(payload["sub"])


# ---------------------------------------------------------------------------
# Endpoints
# ---------------------------------------------------------------------------
@router.post("/recommend", response_model=RecommendResponse)
def recommend(
    _=Depends(_verify_gateway),
    user_id: int = Depends(_current_user_id),
    db: Session = Depends(database.get_db),
):
    """Run the agent loop and return a fresh, evidence-grounded recommendation."""
    try:
        return run_agent(db=db, user_id=user_id)
    except AgentError as e:
        # LLM violated protocol (no save / iteration cap). 502 marks the
        # failure as upstream of our code rather than a server bug.
        raise HTTPException(
            status_code=status.HTTP_502_BAD_GATEWAY,
            detail=f"Agent failed: {e}",
        )


@router.get("/recommend/history", response_model=list[HistoryItem])
def history(
    _=Depends(_verify_gateway),
    user_id: int = Depends(_current_user_id),
    db: Session = Depends(database.get_db),
    limit: int = 10,
):
    """Return the user's most recent agent-generated recommendations."""
    limit = max(1, min(limit, 50))
    rows = (
        db.query(AgentRecommendation)
        .filter(AgentRecommendation.user_id == user_id)
        .order_by(AgentRecommendation.created_at.desc())
        .limit(limit)
        .all()
    )
    return [
        HistoryItem(
            id=r.id,
            content=r.content,
            evidence=r.evidence,
            iterations=r.iterations,
            created_at=r.created_at,
        )
        for r in rows
    ]