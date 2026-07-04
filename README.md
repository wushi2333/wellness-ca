# Wellness CA — AI-Enabled Wellness Mobile App

NUS-ISS SA62 Continuous Assessment Project.

## Repository Structure

```
wellness-ca/
├── CA_Application/               ← Android Studio project (Kotlin)
│   └── app/src/main/java/.../
│       ├── auth/                 Login / JWT auth
│       ├── ui/
│       │   ├── home/             Home, sleep/exercise detail, recommendations
│       │   ├── chat/             Yui chat (Live2D, ASR/TTS, agent)
│       │   ├── add/              Add sleep / exercise bottom sheets
│       │   ├── edit/             Record management (edit / delete)
│       │   ├── chart/            Custom chart views + MPAndroidChart renderers
│       │   ├── bottomnav/        Custom animated bottom navigation bar
│       │   └── common/           Shared adapters
│       ├── live2d/               Live2D Cubism SDK renderer
│       ├── network/              HTTP client (ApiClient, CharacterApi, AgentApi)
│       ├── model/                Data classes (DailyWellness, etc.)
│       └── util/                 Exercise type mapping (i18n)

├── Service_Backend/               ← Spring Boot + Python sidecars + Web UI
│   ├── pom.xml                    Maven project (Java 17, Spring Boot 3.4)
│   ├── src/main/java/.../
│   │   ├── controller/           REST API controllers
│   │   │   └── web/              Thymeleaf web page controllers
│   │   ├── service/              Business logic (character, wellness, auth)
│   │   ├── model/                JPA entities (split schema)
│   │   ├── repository/           Spring Data JPA repos
│   │   ├── security/             JWT + gateway filters
│   │   ├── config/               Security & app configuration
│   │   ├── dto/                  Request/response DTOs
│   │   └── exception/            Global exception handler
│   ├── src/main/resources/
│   │   ├── application.properties
│   │   ├── character-prompts.properties
│   │   ├── templates/web/        Thymeleaf HTML pages
│   │   └── static/web/           CSS & JavaScript
│   └── sidecar/
│       ├── rag/                   RAG (ChromaDB, FastAPI :8001)
│       └── agent/                 Agentic AI (DeepSeek, FastAPI :8002)

└── docs/
    └── api-reference.pdf          Backend API reference
```

## Database Schema (Split Model)

```
wellness_records (daily journal, one row per user per date)
├── id, user_id, record_date (UNIQUE)
├── sleep_record_id → sleep_records.id (0..1)
└── created_at, updated_at

sleep_records
├── id, sleep_hours, sleep_time, wake_time, mood_score, notes
└── created_at, updated_at

exercise_records
├── id, daily_record_id → wellness_records.id
├── exercise_activity, exercise_duration, notes
└── created_at
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

## API Endpoints

### Auth
| Method | Path | Description |
|--------|------|-------------|
| POST | /register | Register |
| POST | /login | Login (JWT) |
| POST | /auth/google | Google OAuth |

### Wellness Records (Split)
| Method | Path | Description |
|--------|------|-------------|
| GET | /records | Paginated daily aggregated records |
| POST | /sleep-records | Create sleep record |
| PUT | /sleep-records/{id} | Update sleep record |
| DELETE | /sleep-records/{id} | Delete sleep record |
| POST | /exercise-records | Create exercise record |
| PUT | /exercise-records/{id} | Update exercise record |
| DELETE | /exercise-records/{id} | Delete exercise record |

### Character (Yui Chat)
| Method | Path | Description |
|--------|------|-------------|
| POST | /character/chat | Chat mode |
| POST | /character/agent | Agent mode (with RAG wellness data) |
| POST | /character/preload-rag | Preload RAG cache |
| GET | /character/rag-ready | Check RAG cache status |
| POST | /character/tts | TTS synthesis |
| POST | /character/asr | ASR recognition |
| GET/POST/DELETE | /character/sessions | Session management |

### Agent (AI Recommendations)
| Method | Path | Description |
|--------|------|-------------|
| POST | /agent/recommend | Generate recommendation |
| GET | /agent/recommend/history | Recommendation history |
| DELETE | /agent/recommend/{id} | Delete recommendation |

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
| Dashboard | Avatar, greeting, sleep & exercise cards with sparkline charts |
| Sleep Detail | Week-navigable gradient bar chart, 7h target line, stat cards |
| Exercise Detail | Week-navigable bar chart, 30min target line, donut chart with Today/Week toggle |
| Record Management | Week-grouped collapsible list, edit via pre-filled bottom sheet, delete with confirmation |
| Yui Chat | Live2D character chat with emotion expressions, TTS voice output |
| Agent Mode | Yui analyzes wellness data via RAG, creates records via intent |
| Voice Input (ASR) | Hold-to-talk with mic icon (gray → green on press) |
| Week Navigation | `<` `>` arrows to switch between weeks with data |
| RAG Preload | Background wellness data cache warm-up for fast agent responses |
| Chinese / English | Full i18n via SharedPreferences, all UI and charts localized |
| Session Management | Multi-select delete, lazy session creation |

## Authors

| Module | Author |
|--------|:--:|
| Spring Boot backend (base) + Character system | Xia Zihang |
| Backend DB/JWT hardening + wellness records | Yutong Luo |
| RAG chatbot + ChromaDB | Huang Qianer |
| Agentic AI recommendation | Cai Peilin |
| Web UI (Thymeleaf) | Guo Jiali |
