"""SQLAlchemy model for agent-generated recommendations.

A separate table from the simple `recommendations` table so that we can
persist the full reasoning trace (which tools were called, what each
returned) alongside the final text. The trace lets the mobile UI show
the user why the AI gave this particular advice.

Author: Cai Peilin
"""
from sqlalchemy import Column, Integer, ForeignKey, Text, JSON, DateTime
from sqlalchemy.sql import func

import database


class AgentRecommendation(database.Base):
    __tablename__ = "agent_recommendations"

    id = Column(Integer, primary_key=True, index=True)
    user_id = Column(Integer, ForeignKey("users.id"), nullable=False, index=True)
    content = Column(Text, nullable=False)
    evidence = Column(JSON, nullable=False)
    iterations = Column(Integer, nullable=False)
    created_at = Column(DateTime, server_default=func.now())