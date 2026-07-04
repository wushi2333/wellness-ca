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

# ── old flat format (backward compat) ──
class SyncReq(BaseModel):
    record_id: int
    user_id: int
    sleep_hours: float = 0.0
    exercise_activity: str = ""
    exercise_duration: int = 0
    record_date: str
    notes: str = ""

# ── new split format ──
class SyncSleepReq(BaseModel):
    record_id: int
    user_id: int
    sleep_hours: float
    sleep_time: str = ""
    wake_time: str = ""
    mood_score: int = 0
    record_date: str
    notes: str = ""

class SyncExerciseReq(BaseModel):
    record_id: int
    user_id: int
    exercise_activity: str
    exercise_duration: int
    record_date: str
    notes: str = ""


@app.post("/search", response_model=SearchResp)
def rag_search(req: SearchReq):
    ctx = chroma_store.search(req.query, req.user_id, req.k)
    return SearchResp(context=ctx)


# Legacy sync (old flat format)
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


@app.post("/sync/sleep")
def rag_sync_sleep(req: SyncSleepReq):
    data = req.model_dump()
    text = chroma_store.textualize_sleep(data)
    chroma_store.upsert_record(req.record_id, req.user_id, text, {
        "source": "realtime", "type": "sleep",
        "record_date": req.record_date,
    }, prefix="sleep")
    return {"status": "ok"}


@app.post("/sync/exercise")
def rag_sync_exercise(req: SyncExerciseReq):
    data = req.model_dump()
    text = chroma_store.textualize_exercise(data)
    chroma_store.upsert_record(req.record_id, req.user_id, text, {
        "source": "realtime", "type": "exercise",
        "record_date": req.record_date,
    }, prefix="exercise")
    return {"status": "ok"}


@app.delete("/sync/{record_id}")
def rag_delete(record_id: int):
    chroma_store.delete_record(record_id, prefix="record")
    return {"status": "ok"}


@app.delete("/sync/sleep/{record_id}")
def rag_delete_sleep(record_id: int):
    chroma_store.delete_record(record_id, prefix="sleep")
    return {"status": "ok"}


@app.delete("/sync/exercise/{record_id}")
def rag_delete_exercise(record_id: int):
    chroma_store.delete_record(record_id, prefix="exercise")
    return {"status": "ok"}


@app.get("/health")
def health():
    return {"status": "running", "count": chroma_store.collection.count()}


if __name__ == "__main__":
    import uvicorn
    uvicorn.run("main:app", host="127.0.0.1", port=8001, reload=False)
