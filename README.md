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
├── Service_Backend/          ← Spring Boot backend (Java)
│   ├── pom.xml               Maven project descriptor
│   ├── src/main/java/.../    22 source files (controllers, services, security, models)
│   └── src/main/resources/   application.properties (env-var placeholders)
│
└── docs/
    └── api-reference.pdf     API endpoint documentation (8 endpoints)
```

## Quick Start

### Android (Team Members)

1. Clone this repo
2. Open `CA_Application/` in Android Studio
3. Run on emulator — connects to `http://152.42.181.66:8000`

### Backend (Server)

```bash
cd Service_Backend
mvn package -DskipTests
java -jar target/wellness-backend-1.0.jar
```

Requires environment variables: `DB_HOST`, `DB_PORT`, `DB_USER`, `DB_PASSWORD`, `DB_NAME`, `JWT_SECRET_KEY`, `ACCESS_TOKEN_EXPIRE_MINUTES`, `DEEPSEEK_API_KEY`, `API_GATEWAY_TOKEN`.

## Architecture

```
Android App ──HTTP──→ Spring Boot (:8000) ──→ DeepSeek API
                              │
                              └──→ Aiven MySQL (cloud)
```

- **DeepSeek API Key** stored only on server — never in Android code
- **Aiven MySQL** accessed only through Spring Boot — never directly from mobile
- **JWT** authentication on all protected endpoints
- **X-API-Token** gateway guard on all endpoints
- JSON responses use **camelCase** field names (Spring Boot default)

## Authors

- Backend: Xia Zihang
- Android: Team members
