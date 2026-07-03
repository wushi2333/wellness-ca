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
│       ├── character/            Yui chat (Live2D, ASR/TTS, agent popup)
│       ├── live2d/               Live2D Cubism SDK renderer
│       ├── network/              HTTP client (ApiClient, CharacterApi)
│       └── model/                Data classes (JSON contracts)

├── Service_Backend/               ← Spring Boot + Python sidecars + Web UI
│   ├── pom.xml                    Maven project (Java 17, Spring Boot 3.4)
│   ├── src/main/java/.../
│   │   ├── controller/           REST API controllers
│   │   │   └── web/              Thymeleaf web page controllers
│   │   ├── service/              Business logic (character, wellness, auth)
│   │   ├── model/                JPA entities
│   │   ├── repository/           Spring Data JPA repos
│   │   ├── security/             JWT + gateway filters
│   │   ├── config/               Security & app configuration
│   │   ├── dto/                  Request/response DTOs
│   │   └── exception/            Global exception handler
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── templates/web/        Thymeleaf HTML pages
│   │   └── static/web/           CSS & JavaScript
│   └── sidecar/
│       ├── rag/                   RAG chatbot (Huang Qianer, FastAPI :8001)
│       └── agent/                 Agentic AI (Cai Peilin, FastAPI :8002)

└── docs/
    └── api-reference.pdf          Backend API reference
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

### Web UI

Open `http://<server>:8000/web/login` in a browser.

Backend notes and test commands:

- `Service_Backend/BACKEND_NOTES.md`
- `Service_Backend/API_TESTING.md`
- `Service_Backend/application.properties.example`

## Architecture

```
Android App ──HTTP──→ Spring Boot :8000 ──→ Aiven MySQL (cloud)
       │                   │
       │        ┌──────────┼────────────┐
       │        ▼          ▼            ▼
       │   DeepSeek API  RAG :8001  Agent :8002
       │                (ChromaDB)  (function-calling)
       │
Web Browser ──HTTP──→ Spring Boot :8000 (Thymeleaf pages)
```

- **DeepSeek API Key** stored only on server
- **Aiven MySQL** accessed only through backend — never from mobile
- **JWT** on all protected API endpoints; **X-API-Token** gateway guard
- **Web UI** uses HttpSession-based auth (bypasses JWT)
- JSON uses **camelCase** (Spring Boot default)

## Features

### Android App

| Feature | Description |
|---|---|
| Dashboard | Navigation hub with floating agent button |
| Agent Popup | Bottom-sheet agent chat (no Live2D) with TTS, ASR, scrim, drag-resize |
| Yui Chat | Live2D character chat with emotion expressions |
| Chat / Agent Mode | Chat casually or let Yui analyze wellness data |
| Voice Input (ASR) | Hold-to-talk via Volcano BigModel ASR |
| Voice Output (TTS) | Volcano TTS with emotion-driven speed/pitch, mouth sync |
| Memory System | User profile extraction + context compression |
| Session Management | Multi-select delete, pin to top, lazy session creation |
| Tools Visualization | Collapsible "tools▸" indicator for agent reasoning |
| Cold-start Welcome | Greeting message with user's name (local only) |
| Live2D Caching | CPU model data survives activity restarts |

### Backend API

| Endpoint | Description |
|---|---|
| `POST /register`, `POST /login` | Auth (JWT) |
| `GET/POST /records`, `PUT/DELETE /records/{id}` | Wellness CRUD |
| `POST /chat` | AI chatbot |
| `POST /character/chat` | Character chat mode |
| `POST /character/agent` | Agent mode (wellness analysis + navigation) |
| `POST /character/tts` | Volcano TTS synthesis |
| `POST /character/asr` | Volcano ASR recognition |
| `GET /character/sessions` | List chat sessions |
| `POST /character/sessions` | Create chat session |
| `DELETE /character/sessions/{id}` | Delete chat session |
| `GET /character/sessions/{id}/messages` | Load message history |
| `GET /recommendations` | AI-generated recommendations |

### Web UI

| Page | Route |
|---|---|
| Login | `/web/login` |
| Register | `/web/register` |
| Dashboard | `/web/dashboard` |
| Wellness Records | `/web/records` |
| New / Edit Record | `/web/records/new`, `/web/records/{id}/edit` |
| Record Detail | `/web/records/{id}` |
| AI Chat | `/web/chat` |
| AI Insights | `/web/insights` |
| Insight History | `/web/insight-history` |

## Authors

| Module | Author |
|--------|:--:|
| Spring Boot backend (base) + Character system | Xia Zihang |
| Backend DB/JWT hardening + wellness records | Yutong Luo |
| RAG chatbot + ChromaDB | Huang Qianer |
| Agentic AI recommendation | Cai Peilin |
| Web UI (Thymeleaf) | Guo Jiali |
| Android app | Wang Songyu, Liu Yu, Xia Zihang |
