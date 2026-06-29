# Author: Huang Qianer
import os, requests
import chromadb
from chromadb.utils import embedding_functions

EMBEDDING_BASE_URL = os.getenv("EMBEDDING_BASE_URL", "https://openrouter.ai/api/v1")
OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "nvidia/llama-nemotron-embed-vl-1b-v2:free")


class RemoteEmbedding(embedding_functions.EmbeddingFunction):
    """OpenRouter-based embedding function for ChromaDB."""
    def __call__(self, texts: list[str]) -> list[list[float]]:
        headers = {"Authorization": f"Bearer {OPENROUTER_API_KEY}", "Content-Type": "application/json"}
        embeddings = []
        for text in texts:
            resp = requests.post(f"{EMBEDDING_BASE_URL}/embeddings",
                json={"model": EMBEDDING_MODEL, "input": text}, headers=headers, timeout=30)
            resp.raise_for_status()
            embeddings.append(resp.json()["data"][0]["embedding"])
        return embeddings


_chroma = chromadb.PersistentClient(path="./chroma_data")
_ef = RemoteEmbedding()

collection = _chroma.get_or_create_collection(
    name="wellness_knowledge_base",
    embedding_function=_ef,
)


def search(query: str, user_id: int, k: int = 4) -> str:
    """Retrieve top-k semantically relevant chunks for a user from ChromaDB."""
    if collection.count() == 0:
        return ""
    results = collection.query(
        query_texts=[query],
        n_results=k,
        where={"user_id": user_id},
    )
    docs = results.get("documents")
    if not docs or not docs[0]:
        return ""
    return "\n\n".join(docs[0])


def upsert_record(record_id: int, user_id: int, text: str, meta: dict) -> None:
    """Insert or update a single record in ChromaDB."""
    collection.upsert(
        ids=[f"record_{record_id}"],
        documents=[text],
        metadatas=[{**meta, "user_id": user_id, "record_id": record_id}],
    )


def delete_record(record_id: int) -> None:
    """Remove a record from ChromaDB by its record_id."""
    collection.delete(ids=[f"record_{record_id}"])


def textualize(sleep_hours: float, exercise_activity: str,
               exercise_duration: int, record_date: str, notes: str) -> str:
    """Convert a structured wellness record into a natural-language chunk."""
    activity = exercise_activity if exercise_activity else "No exercise performed"
    dur = exercise_duration if exercise_duration else 0
    nt = notes if notes else "No notes"
    return (
        f"Date: {record_date}. Health Record: "
        f"Sleep duration was {sleep_hours} hours. "
        f"Exercise: {activity}, lasted for {dur} minutes. "
        f"User Notes: {nt}."
    )
