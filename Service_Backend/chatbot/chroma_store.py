# Author: Huang Qianer
import os
import chromadb
from openai import OpenAI
from chromadb.utils import embedding_functions

EMBEDDING_BASE_URL = os.getenv("EMBEDDING_BASE_URL", "https://openrouter.ai/api/v1")
OPENROUTER_API_KEY = os.getenv("OPENROUTER_API_KEY")
EMBEDDING_MODEL = os.getenv("EMBEDDING_MODEL", "nvidia/llama-nemotron-embed-vl-1b-v2:free")


class embed(embedding_functions.EmbeddingFunction):


    def __call__(self, input: list[str]) -> list[list[float]]:
        client = OpenAI(base_url=EMBEDDING_BASE_URL, api_key=OPENROUTER_API_KEY)
        embeddings = []
        for text in input:
            response = client.embeddings.create(
                input=text,
                model=EMBEDDING_MODEL
            )
            embeddings.append(response.data[0].embedding)
        return embeddings


# 初始化 Chroma 持久化客户端
chroma_client = chromadb.PersistentClient(path="./chroma_data")
embed_fn = embed()

# 获取或创建集合
collection = chroma_client.get_or_create_collection(
    name="wellness_knowledge_base",
    embedding_function=embed_fn
)


def retrieve(query: str, user_id: int, k: int = 4) -> str:

    #语义检索：根据问题和当前 user_id 检索向量数据库中最相关的文档块。

    if collection.count() == 0:
        return ""

    # 加入 where={"user_id": user_id} 过滤条件
    results = collection.query(
        query_texts=[query],
        n_results=k,
        where={"user_id": user_id}  # 限制当前用户只能检索到自己的文档块
    )

    if not results['documents'] or not results['documents'][0]:
        return ""

    return "\n\n".join(results['documents'][0])