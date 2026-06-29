# Author: Huang Qianer
"""RAG sidecar - ChromaDB vector store with HTTP API for the Spring Boot backend."""
from fastapi import FastAPI
from pydantic import BaseModel
import chroma_store

app = FastAPI(title="Wellness RAG Sidecar", version="1.0")


class SearchReq(BaseModel):
    query: str
    user_id: int
    k: int = 4

class SearchResp(BaseModel):
    context: str

class SyncReq(BaseModel):
    record_id: int
    user_id: int
    sleep_hours: float
    exercise_activity: str = ""
    exercise_duration: int = 0
    record_date: str
    notes: str = ""


@app.post("/search", response_model=SearchResp)
def rag_search(req: SearchReq):
    ctx = chroma_store.search(req.query, req.user_id, req.k)
    return SearchResp(context=ctx)


@app.post("/sync")
def rag_sync(req: SyncReq):
    text = chroma_store.textualize(
        req.sleep_hours, req.exercise_activity,
        req.exercise_duration, req.record_date, req.notes,
    )
    chroma_store.upsert_record(req.record_id, req.user_id, text, {
        "source": "realtime",
        "record_date": req.record_date,
    })
    return {"status": "ok"}


@app.delete("/sync/{record_id}")
def rag_delete(record_id: int):
    chroma_store.delete_record(record_id)
    return {"status": "ok"}


@app.get("/health")
def health():
    return {"status": "running", "count": chroma_store.collection.count()}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8001, reload=False)
