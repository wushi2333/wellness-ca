# Author: Huang Qianer
import os
from openai import OpenAI
import chroma_store

CHAT_MODEL = os.getenv("CHAT_MODEL", "DeepSeek-V4-Flash")
_deepseek = OpenAI(
    base_url="https://api.deepseek.com",
    api_key=os.getenv("DEEPSEEK_API_KEY"),
)


def build_prompt(context: str) -> str:
    #构建注入了检索上下文的系统提示词
    base_prompt = (
        "You are a friendly wellness assistant. "
        "Give concise, practical health advice. "
        "Answer the user's question based on the context provided below. "
        "If the context is empty or doesn't contain the answer, rely on your existing knowledge."
    )
    if context:
        return f"{base_prompt}\n\nContext information:\n{context}"
    return base_prompt


def generate_reply(question: str, history: list, user_id: int) -> str:
    #生成 LLM 回复，使用结构化的消息对象并截断历史记录
    # 传入 user_id 触发带隔离机制的 RAG 检索
    context = chroma_store.retrieve_context(question, user_id, k=4)


    # 2. 构建系统提示词
    messages = [{"role": "system", "content": build_prompt(context)}]

    # 3. 截断历史记录（Sliding Window），仅保留最近的 5 轮对话以优化性能
    MAX_TURNS = 5
    recent_history = history[-MAX_TURNS:] if len(history) > MAX_TURNS else history

    # 4. 组装结构化消息对象（Structured Message Objects）
    for msg in recent_history:
        messages.append({"role": "user", "content": msg.question})
        messages.append({"role": "assistant", "content": msg.answer})

    messages.append({"role": "user", "content": question})

    # 5. 调用模型获取回答
    resp = _deepseek.chat.completions.create(
        model=CHAT_MODEL,
        messages=messages,
        temperature=0.7,
        max_tokens=512,
    )

    return resp.choices[0].message.content