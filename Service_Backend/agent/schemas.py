"""Request and response models for the /agent/* endpoints.

Author: Cai Peilin
"""
from datetime import datetime
from pydantic import BaseModel


class ToolTrace(BaseModel):
    name: str
    summary: str


class RecommendResponse(BaseModel):
    recommendation: str
    evidence: list[ToolTrace]
    iterations: int
    saved_id: int


class HistoryItem(BaseModel):
    id: int
    content: str
    evidence: list[ToolTrace]
    iterations: int
    created_at: datetime