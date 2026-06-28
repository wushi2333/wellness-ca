# Author: Huang Qianer
import sys
from pathlib import Path

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel
from sqlalchemy.orm import Session
import database
import chatbot

sys.path.append(str(Path(__file__).parent.parent))

router = APIRouter()


class ChatRequest(BaseModel):
    message: str


class ChatResponse(BaseModel):
    reply: str


@router.post("/chat", response_model=ChatResponse)
def chat(request: ChatRequest,
         _=Depends(lambda: __import__('main').verify_gateway()),
         user_id: int = Depends(lambda auth_header=None: __import__('main').current_user_id(auth_header)),
         db: Session = Depends(database.get_db)):
    from main import ChatHistory  # 为了避免循环导入，我们在函数内部导入 main.py 中的依赖项和模型, 引入 ORM 模型
    # 1. 验证请求
    if not request.message or not request.message.strip():
        raise HTTPException(status_code=400, detail="Message is required.")

    # 获取当前用户的历史记录，按照时间升序排列
    history_records = db.query(ChatHistory).filter(ChatHistory.user_id == user_id).order_by(
        ChatHistory.created_at.asc()).all()

    # 2. 调用 LLM 服务层获取回复
    try:
        reply = chatbot.generate_reply(request.message, history_records, user_id)
    except Exception as error:
        raise HTTPException(status_code=500, detail=str(error)) from error

    # 3. 将新对话保存到数据库持久化
    db.add(ChatHistory(user_id=user_id, question=request.message, answer=reply))
    db.commit()

    return ChatResponse(reply=reply)