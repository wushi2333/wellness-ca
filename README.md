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

├── RecordYourWellnessApp/         ← .NET 10 WinForms desktop port (same backend/API)

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

The backend is built **on the server**. Package the source, upload, build, 
and restart the systemd service:

```bash
# From the dev machine
cd Service_Backend
tar czf /tmp/sb_src.tgz -C . src pom.xml
scp /tmp/sb_src.tgz root@152.42.181.66:/tmp/

# On the server
cd /home/wellness-dev/wellness-backend
rm -rf src && tar xzf /tmp/sb_src.tgz
mvn -q package -DskipTests
systemctl restart wellness-backend   # ~80s cold start (Hibernate DDL + pool warmup)
```

Runtime env vars (set in the `wellness-backend.service` unit): `DB_HOST`, `DB_PORT`,
`DB_USER`, `DB_PASSWORD`, `DB_NAME`, `JWT_SECRET_KEY`, `DEEPSEEK_API_KEY`,
`API_GATEWAY_TOKEN`, `VOLCANO_TTS_APPID/TOKEN/SPEAKER`.

> **Note:** the backend listens on **HTTP :8000** (for curl/API clients) **and**
> **HTTPS :8443** (self-signed cert, for browsers — required for microphone/ASR and
> Google OAuth). Browsers should use `https://152.42.181.66:8443`.

### Web UI

Open `(https://152-42-181-66.nip.io/web/login)` in a browser (accept the self-signed cert
warning once). The Python sidecars must be running for agent chat and recommendations:

```bash
# On the server (already set up as systemd units: agent-sidecar, rag-sidecar)
systemctl status agent-sidecar rag-sidecar   # :8002 agent, :8001 RAG
```

## API Endpoints

### Auth
| Method | Path | Description |
|--------|------|-------------|
| POST | /register | Register |
| POST | /login | Login (JWT) |
| POST | /auth/google | Google OAuth (authCode or idToken) |
| GET | /web/auth/google | Web: start Google OAuth redirect |
| GET | /web/auth/google/callback | Web: Google OAuth callback |

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
       │                   │  :8443 (HTTPS, self-signed)
       │        ┌──────────┼────────────┐
       │        ▼          ▼            ▼
       │   DeepSeek API  RAG :8001  Agent :8002
       │                (ChromaDB)  (function-calling)
       │
Web Browser ──HTTPS:8443──→ Spring Boot (Thymeleaf + Turbo)
       │                       │
       │  Live2D (Cubism 4) rendered client-side via PixiJS
       │  ASR/TTS proxied through /web/chat/{asr,tts} (session auth)
       │
WinForm (RecordYourWellnessApp) ──HTTP:8000──→ same backend (REST + JWT)
```

- **DeepSeek API Key** stored only on server
- **Aiven MySQL** accessed only through backend — never from mobile
- **JWT** on all protected API endpoints; **X-API-Token** gateway guard
- **Web UI** uses HttpSession-based auth (bypasses JWT); **HTTPS :8443** required for microphone (ASR) and Google OAuth
- **Live2D** runs entirely in the browser (Cubism Core + PixiJS) — server only serves the ~23MB model assets
- **Agent navigation intent** (`navigate`/`create_record`) is a shared contract across Android, Web, and WinForm — only the target→URL mapping is web-specific
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

### Web UI (`Service_Backend` — Thymeleaf + Turbo Drive)
| Feature | Description |
|---|---|
| Pages | Login, Register, Dashboard, Records, Sleep/Exercise forms & detail, Chat, Insights + history, Profile, Settings, Change password, Google OAuth resolve/username |
| Global i18n | Full Chinese/English toggle via `web-messages_*.properties` (Spring `MessageSource` + session locale); brand name stays English |
| Turbo Drive | SPA-like navigation without full reloads; `data-turbo-permanent` ambient layer & companion persist across pages |
| Visual polish | Macaron gradient background, mouse-follow glow (behind cards), click ripples, lightweight page-transition fade |
| Yui Chat (Live2D) | YouXiaoMiao model (Cubism 4) in a fixed upper-body stage; head sway, irregular blink, breath, drag-follow, tap-for-expression (blackFace/tears/cry), 5s auto-recover — ported 1:1 from Android |
| Chat ASR/TTS | Hold-to-record mic (AudioWorklet 16kHz PCM → `/web/chat/asr`); reply auto-spoken via `/web/chat/tts` (Volcano) with TTS on/off toggle; lip-sync hook (`window.WEB_TTS_LEVEL`) drives Live2D mouth |
| Instant send | Optimistic user bubble + typing spinner; fetch-based (no page reload) so the Live2D canvas survives |
| Collapsible rail | Chat history sidebar collapses; main area expands; state persisted |
| Agent titles | Chat header shows the LLM-generated short title (not `Session #id`) |
| Floating companion | Draggable Yui orb on non-chat pages; head tracks mouse; click opens an agent popup that can navigate (`navigate` intent → `/web/sleep-detail` etc., shared contract with Android/WinForm) |
| Google OAuth | `Sign in with Google` on login/register → `/web/auth/google` → callback handles direct-login / email-conflict / new-user-username branches |
| Week navigation | Circular primary-colored prev/next arrows with disabled states (no older data / already latest week) |

## Authors

| Module | Author |
|--------|:--:|
| Spring Boot backend (base) + Character system + Google OAuth | Xia Zihang |
| Backend DB/JWT hardening + wellness records | Yutong Luo |
| RAG chatbot + ChromaDB | Huang Qianer |
| Agentic AI recommendation | Cai Peilin |
| Web UI (Thymeleaf) | Guo Jiali |
