# Wellness CA — AI-Enabled Wellness Mobile App

NUS-ISS SA62 Continuous Assessment Project.

## Repository Structure

```
wellness-ca/
├── CA_Application/               ← Android Studio project (Kotlin)
│   └── app/src/main/java/.../
│       ├── auth/                 Login / JWT auth
│       ├── wellness/             Wellness records CRUD
│       ├── chat/                 AI chatbot UI
│       ├── agentic/              AI recommendations
│       ├── network/              HTTP client (ApiClient)
│       └── model/                Data classes (JSON contracts)

├── Service_Backend/               ← Spring Boot + Python sidecars
│   ├── pom.xml                    Maven project (Java 17, Spring Boot 3.4)
│   ├── src/main/java/.../         Controllers, services, security, models
│   ├── src/main/resources/        application.properties (env-var placeholders)
│   └── sidecar/
│       ├── rag/                   RAG chatbot (Huang Qianer, FastAPI :8001)
│       └── agent/                 Agentic AI (Cai Peilin, FastAPI :8002)

└── docs/
    └── agentic-api.md             Agentic AI API specification
```

## Quick Start

### Android

```bash
git clone https://github.com/wushi2333/wellness-ca.git
# Open CA_Application/ in Android Studio → Run
```

### Backend

```bash
cd Service_Backend
mvn package -DskipTests
java -jar target/wellness-backend-1.0.jar
```

Requires: `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `JWT_SECRET_KEY`, `DEEPSEEK_API_KEY`, `API_GATEWAY_TOKEN`.

## Architecture

```
Android App ──HTTP──→ Spring Boot :8000 ──→ Aiven MySQL (cloud)
                         │
            ┌────────────┼────────────┐
            ▼            ▼            ▼
       DeepSeek API   RAG :8001   Agent :8002
                      (ChromaDB)  (function-calling)
```

- **DeepSeek API Key** stored only on server
- **Aiven MySQL** accessed only through backend — never from mobile
- **JWT** on all protected endpoints; **X-API-Token** gateway guard on all
- JSON uses **camelCase** (Spring Boot default)

## Authors

| Module | Author |
|--------|:--:|
| Spring Boot backend (base) | Xia Zihang |
| RAG chatbot + ChromaDB | Huang Qianer |
| Agentic AI recommendation | Cai Peilin |
| Android app | Team members |
