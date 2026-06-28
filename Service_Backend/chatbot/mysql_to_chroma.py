# Author: Huang Qianer
import os
from sqlalchemy.orm import Session
from main import database
from main import WellnessRecord
from chroma_store import collection, embed_fn


def textualize_record(record: WellnessRecord) -> str:

    #将结构化的 MySQL 行转化为具有语义的自然语言文本块。

    activity = record.exercise_activity if record.exercise_activity else "No exercise performed"
    duration = record.exercise_duration if record.exercise_duration else 0
    notes = record.notes if record.notes else "No notes"

    text_chunk = (
        f"Date: {record.record_date}. Health Record: "
        f"Sleep duration was {record.sleep_hours} hours. "
        f"Exercise: {activity}, lasted for {duration} minutes. "
        f"User Notes: {notes}."
    )
    return text_chunk


def ingest_mysql_to_chroma(user_id: int):
    #从 MySQL 提取特定用户的数据，分块并注入到 ChromaDB 中
    # 1. Connect to MySQL to retrieve data
    db: Session = next(database.get_db())
    records = db.query(WellnessRecord).filter(WellnessRecord.user_id == user_id).all()

    if not records:
        print(f"User {user_id} has no wellness records in MySQL.")
        return

    documents = []
    metadatas = []
    ids = []

    # 2. 对数据库记录进行文本化分块处理
    for record in records:
        chunk_text = textualize_record(record)
        documents.append(chunk_text)

        # Store raw data as metadata for future filtering or tracing
        metadatas.append({
            "source": "mysql",
            "user_id": record.user_id,
            "record_date": str(record.record_date),
            "record_id": record.id
        })
        ids.append(f"record_{record.id}")

    # 3. Inject into Chroma vector database
    # Note: Chroma's add/upsert methods will call embed_fn configured in chroma_store.py at the lower level
    print(f"Embedding and storing {len(documents)} records into vector database...")
    collection.upsert(
        ids=ids,
        documents=documents,
        metadatas=metadatas
    )

    print(f"Successfully synced user {user_id}'s MySQL data to ChromaDB!")


if __name__ == "__main__":
    # As a utility script, you can manually input user_id to sync their data
    target_user = input("Enter User ID to sync wellness data: ")
    try:
        ingest_mysql_to_chroma(int(target_user))
    except Exception as e:
        print(f"Error occurred: {e}")