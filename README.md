# Wellness CA — AI-Enabled Wellness Mobile App

NUS-ISS SA62 Continuous Assessment Project.

## Repository Structure

```
wellness-ca/
├── CA_Application/          ← Android Studio project (Kotlin)
│   ├── app/src/main/java/.../auth/        Login / JWT auth
│   ├── app/src/main/java/.../wellness/    Wellness records CRUD
│   ├── app/src/main/java/.../chat/        AI chatbot UI
│   ├── app/src/main/java/.../agentic/     AI recommendations
│   ├── app/src/main/java/.../network/     HTTP client (ApiClient)
│   └── app/src/main/java/.../model/       Data classes (JSON contracts)
│
├── Service_Backend/          ← FastAPI backend (Python)
│   ├── main.py               API endpoints (8 total)
│   ├── database.py           Aiven MySQL via SQLAlchemy (SSL)
│   ├── security.py           JWT create/verify + bcrypt
│   ├── ca.pem                Aiven public CA certificate
│   ├── .env.example          Environment variable template
│   └── requirements.txt      Python dependencies
│
└── docs/
    └── api-reference.pdf     FastAPI endpoint documentation
```

## Quick Start

### Android (Team Members)

1. Clone this repo
2. Open `CA_Application/` in Android Studio
3. Run on emulator — connects to `http://152.42.181.66:8000`

### Backend (Server)

```bash
cd Service_Backend
pip install -r requirements.txt
cp .env.example .env   # edit with real values
uvicorn main:app --host 0.0.0.0 --port 8000
```

## Architecture

```
Android App ──HTTP──→ FastAPI (:8000) ──→ DeepSeek API
                              │
                              └──→ Aiven MySQL (cloud)
```

- **DeepSeek API Key** stored only on server — never in Android code
- **Aiven MySQL** accessed only through FastAPI — never directly from mobile
- **JWT** authentication on all protected endpoints
- **X-API-Token** gateway guard on all endpoints

## Authors

- Backend: Xia Zihang
- Android: Team members
