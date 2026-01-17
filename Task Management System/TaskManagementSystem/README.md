# Task Management System

An end-to-end task management app built as a monorepo:

- Backend: Spring Boot (Java 21, Gradle) + MongoDB + JWT auth
- Frontend: React (Vite) with a modern Kanban UI

The project is designed for real workflow use: task planning (Board/Calendar/Timeline), insight (Analytics), admin tooling, and a polished UI (light/dark themes).

## Repo Layout

- backend/ — Spring Boot API (Gradle wrapper included)
- client/ — React app (Vite)
- docker-compose.yml — local MongoDB + optional mongo-express
- .env.example — optional root env (docker-compose overrides)

## Features

- Authentication: register/login + JWT
- Kanban board: create/update/delete, drag & drop move + reorder, WIP limits
- Task details: comments, checklist, labels, dependencies, decisions, activity feed
- Time: focus mode, Pomodoro, time budget tracking, timers with notes
- Planning views: Calendar (due dates), Timeline (history), Analytics (trends)
- Archive model (instead of delete):
  - DONE tasks can auto-archive after N days (board hides them by default)
  - Timeline keeps history
  - Archived page shows archived items with unarchive
- Offline-friendly UX (client queues requests when offline and replays when online)
- Admin dashboard (ADMIN role): global tasks overview + group/user assignment
- Optional email notifications and optional AI template-assist endpoints

## Prerequisites

- Java 21
- Node.js 18+ (20+ recommended)
- Docker Desktop (recommended for local MongoDB)

## Quickstart (Local Dev)

1. Start MongoDB

```powershell
cd "c:\Practice Work\Projects\Task Management System\TaskManagementSystem"
docker compose up -d
```

2. Start backend

```powershell
cd backend
Copy-Item .env.example .env
\gradlew.bat bootRun
```

3. Start frontend

```powershell
cd client
Copy-Item .env.example .env
npm install
npm run dev
```

Default dev URLs:

- Backend: http://localhost:8081
- Frontend: http://localhost:5173
- Mongo-express (optional): http://localhost:8082

## Environment Variables

This repo supports local .env files for convenience. Never commit real secrets.

Spring Boot loads backend/.env automatically via:

- backend/src/main/resources/application.properties
  - spring.config.import=optional:file:.env[.properties]

### Root (.env.example)

Used for docker-compose overrides.

- MONGO_PORT (default 27017)
- MONGO_EXPRESS_PORT (default 8082)
- MONGO_EXPRESS_USER / MONGO_EXPRESS_PASS (defaults admin/admin)

### Backend (backend/.env.example)

Required for running the backend:

- SERVER_PORT
- SPRING_DATA_MONGODB_URI
- JWT_SECRET (32+ chars recommended)

Common configuration:

- CORS_ALLOWED_ORIGINS (comma-separated, e.g. http://localhost:5173)
- JWT_EXPIRATION_MS (default 86400000)
- TASKS_ARCHIVE_DONE_AFTER_DAYS (default 1; set 0 to disable)

Optional:

- CLIENT_BASE_URL (used to build links in emails)
- MAIL_ENABLED / MAIL_FROM / SMTP_HOST / SMTP_PORT / SMTP_USERNAME / SMTP_PASSWORD
- AI_ENABLED / AI_PROVIDER / AI_API_KEY
- ADMIN_EMAIL / ADMIN_PASSWORD (local bootstrap only; do not use in production)

### Client (client/.env.example)

Dev:

- VITE_API_PROXY_TARGET=http://localhost:8081 (Vite proxies /api to backend)

Optional (prod deployments where client and API are on different origins):

- VITE_API_BASE_URL=https://api.yourdomain.com

## API Overview

Primary routes:

- /api/auth
  - POST /register
  - POST /login
  - GET /me
- /api/tasks
  - GET / (list; also applies DONE auto-archive)
  - POST / (create)
  - PUT /{id} (update)
  - DELETE /{id}
  - POST /move (board move/reorder)
  - POST /bulk
  - PUT /{id}/archive
  - PUT /{id}/labels
  - POST /{id}/comments
  - POST /{id}/checklist, PUT /{id}/checklist/{itemId}, POST /{id}/checklist/reorder
  - PUT /{id}/dependencies
  - POST /{id}/timer/start, POST /{id}/timer/stop
  - PUT /{id}/recurrence
- /api/analytics (overview + trends)
- /api/admin (ADMIN role)
- /api/ai (template-only by default)

## Task Archiving (Board vs Timeline)

- Board page hides archived tasks by default (toggleable).
- Timeline intentionally shows archived tasks so history is preserved.
- Archived page shows only archived tasks and allows unarchive.

Auto-archive rule:

- When listing tasks, if a task is DONE and completedAt is older than TASKS_ARCHIVE_DONE_AFTER_DAYS, it is marked archived=true and archivedAt is set.

## Testing

Backend:

```powershell
cd backend
\gradlew.bat test
```

Note: some tests may use Testcontainers (Docker required).

Client:

```powershell
cd client
npm run lint
npm run build
```

## Email (Optional)

Email sending is disabled by default. To enable:

1. Copy and edit backend/.env

```powershell
cd backend
Copy-Item .env.example .env
notepad .env
```

2. Set MAIL*ENABLED=true and SMTP*\* variables.

3. Run the test script:

```powershell
pwsh -NoProfile -File .\scripts\send-test-email-end-to-end.ps1 -RecipientEmail "your.email@example.com"
```

## Deployment Notes

- Do not run with development .env values in production.
- Set a strong JWT_SECRET and explicit CORS_ALLOWED_ORIGINS.
- Consider terminating TLS at a reverse proxy and hosting the API and client on appropriate origins.
- The backend includes a prod-profile startup validator that fails fast when CORS origins are not configured.

## Troubleshooting

- Frontend looks stale after refactors: restart npm run dev.
- Port conflicts: change SERVER_PORT / Vite port as needed.
- Mongo connection issues: confirm docker compose is up and SPRING_DATA_MONGODB_URI matches.
