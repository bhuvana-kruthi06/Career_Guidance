# Project handover — Pathfinder AI Career Guidance

**Workspace:** `C:\Users\Bhuvana Kruthi\OneDrive\Documents\AI-Career-Guidance`  
**As of:** 2026-10-27  
**Git:** `git status` returned “not a git repository”; `.git` is absent. No commit history/status is available.

## 1. Project overview

Build a secure, tested, demonstrable career guidance and skill-gap platform for campus placement interviews/portfolio use. It recommends O*NET occupations from a short profile, self-rated strengths, and interests; explains profile similarity, shows gaps/resources, and supports accounts/history when enabled. Preserve the existing matching formula and public guest flow; do not add paid/cloud/LLM services or invent questionnaire content. Stack: React 19 + Vite 7, Spring Boot 3.5.5 / Java 24 / Maven, Python 3.15 + pandas/NumPy matcher subprocess, optional MySQL 8 + Flyway, Vitest and JUnit/Spring tests.

## 2. Current status

| Status | Features |
|---|---|
| **Implemented; prior checks passed** | Phases 1–8: safe browser storage and guest flow; router/pages/settings/interview drafts; backend limits, health, CORS, matcher timing; optional BCrypt/JWT/rotating refresh-cookie auth; Flyway persistence for profiles/sessions/results/saved careers/roadmap; career discovery and explainable results; curated skill resources; what-if simulator and account history chart. The matcher formula was documented and left unchanged. See phase reports below. |
| **Implemented; checks pass** | Phases 1–8. Phase 9 API docs, Dockerfiles/Compose, Playwright journey, Dependabot/CI, architecture/ADRs/limitations/demo, and documentation were added. User chose a local, read-only mount for generated `data/processed/`; no data is baked into images or committed. |
| **Partially implemented** | Phase 9 clean-clone stack and E2E setup exist but have not been run: Docker CLI is absent and Playwright Chromium is not installed. No Phase 9 acceptance report claimed full success; see `reports/PHASE9_2026-10-27.md`. |
| **Unverified** | Docker build/compose/startup, container health/migrations, live MySQL restart, browser E2E, GitHub Actions run, OWASP dependency-check findings, visual/accessibility audit, and resource link availability. `data/processed/` must be generated locally before compose. |

Phase reports present: `reports/BASELINE_2026-10-27.md`, `PHASE1_2026-10-27.md` through `PHASE9_2026-10-27.md`, and `PHASE3_BENCHMARK_2026-10-27.md`.

Latest checks: Python **87 passed**; CI-equivalent Python **76 passed, 11 dataset-path assertions deselected**; backend **23 passed**; frontend **32 passed**; frontend build passed; npm audit **0 vulnerabilities**. Playwright lists one E2E test but it was not run. Link-check script could not reach the network; links remain UNVERIFIED.

## 3. Exact progress and next step

- **Last completed action:** completed `reports/PHASE9_2026-10-27.md` and updated this handover to reflect Phase 9 work.
- **Current state:** Phase 9 is implemented but Docker/E2E/CI acceptance is unverified; CLI check found no Docker executable and Chromium cache is absent.
- **Very next step:** install/start Docker Desktop, confirm `data/processed/` exists, copy `.env.example` to `.env`, replace local DB passwords, then run `docker compose config` and `docker compose up --build`. User already chose a read-only local processed-data mount. After containers are healthy, install Chromium and run the documented Playwright journey.

## 4. Important project paths

- `README.md` — Windows quick start, dataset preparation, account setup, feature list, test commands.
- `PROJECT_IMPLEMENTATION_AUDIT.md` — source audit / project context.
- `data_processing/matching_engine.py` — canonical scoring formula; **do not change**.
- `data_processing/` — raw-data inspection, cleaning, normalization, occupation map, frontend skill export.
- `data/raw/` — licensed O*NET/ESCO source files (git-ignored; may not exist on a clean clone).
- `data/processed/` — generated occupation/skill files required for matching and career search (git-ignored; must be regenerated or otherwise supplied).
- `backend/src/main/java/edu/careerguidance/` — REST API, matcher bridge, auth, persistence, catalog/services/security.
- `backend/src/main/resources/db/migration/V1__create_auth_tables.sql`, `V2__create_account_data.sql` — Flyway tables.
- `backend/pom.xml` — Maven backend dependencies; latest interrupted work added springdoc and has not been built.
- `frontend/src/` — routed UI, API, auth, pages/components/styles/tests.
- `frontend/e2e/career-journey.spec.js`, `frontend/playwright.config.js` — new E2E setup; requires real app data/backend/MySQL and has not run.
- `.github/workflows/ci.yml`, `.github/dependabot.yml` — new CI and dependency update config; unverified.
- `docs/MATCHING.md`, `docs/ARCHITECTURE.md`, `docs/adr/`, `docs/LIMITATIONS.md` — score formula, diagram, decisions, limits.
- `data/resources/skill_resources.json`, `scripts/check_links.py`, `scripts/demo.ps1` — learning resource catalog, HTTPS link checker, demo guide.
- `docker-compose.yml` — MySQL, backend, and frontend services; backend bind-mounts `PROCESSED_DATA_DIR` read-only.
- `start_backend.ps1` — starts backend/matcher; consult this and README when diagnosing startup.

## 5. Database

MySQL 8.4 is opt-in for account mode. Existing `backend/schema.sql` is legacy design and is not the runtime migration source. Flyway V1 creates `users` and `refresh_tokens`; V2 adds owner-linked profile, match session/result, saved career, and roadmap tables (five account-data tables). Sessions/results are linked to users, results have unique `(session_id, result_rank)`, and owner data uses cascading cleanup. Career references use O*NET SOC code. Tests used H2 in MySQL mode; live MySQL durability/restart has not been verified.

`docker-compose.yml` starts only MySQL and requires `.env`. `.env.example` contains placeholders; **never copy or reveal secret values**. Account setup needs `AUTH_ENABLED=true`, `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, a private random `JWT_SECRET` (at least 32 bytes), and `VITE_AUTH_ENABLED=true` before starting Vite. Local HTTP uses `AUTH_COOKIE_SECURE=false`; HTTPS deployment should set it to true. Flyway runs only when auth is enabled. Default guest mode does not need MySQL.

## 6. Environment and run commands

Required per README: Python 3.15, Java 24, Maven (`mvn` on PATH or `MAVEN_CMD`), Node/npm. Raw O*NET 31.0 and ESCO v1.2.1 files are required to regenerate `data/processed/`. Keep secret values in environment variables / ignored `.env`; do not print them.

From project root, PowerShell (data commands only when licensed raw files are available):

```powershell
python -m venv venv
.\venv\Scripts\python.exe -m pip install -r requirements.txt
.\venv\Scripts\python.exe data_processing\inspect_data.py
.\venv\Scripts\python.exe data_processing\clean_data.py
.\venv\Scripts\python.exe data_processing\normalize_skills.py
.\venv\Scripts\python.exe data_processing\build_career_skill_map.py
.\venv\Scripts\python.exe frontend\scripts\export_skills.py
.\start_backend.ps1
```

In a second terminal:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Then visit the Vite URL (normally `http://localhost:5173`); backend default is `http://127.0.0.1:8091`. Checks: `python -m pytest --basetemp .pytest-workspace-temp`; `mvn -f backend\pom.xml test`; from `frontend`: `npm test -- --run`, `npm run build`, `npm audit --audit-level=high`, `npm run test:e2e` (the E2E prerequisites above apply). The last exact run counts are documented in `reports/PHASE4_2026-10-27.md` and `reports/PHASE5_2026-10-27.md`, but are pre-Phase-9.

## 7. Errors and blockers

- User reported Windows `MYSQL80` service says it starts and then stops. No MySQL server log/Event Viewer diagnosis is recorded. Current app compose uses a separate Docker MySQL service; local Windows MySQL service is not required for guest flow. Don’t guess at the cause: inspect MySQL error log and Windows Event Viewer if local service is still needed.
- User saw `Failed to fetch`/matching service offline in UI. Backend defaults to port 8091 and invokes Python; first verify `data/processed/` exists, then start `start_backend.ps1` and check `GET http://127.0.0.1:8091/api/health`. Earlier Docker was unavailable and a local port/listener issue was not changed.
- Previous terminal link checks failed due to outbound network/DNS restrictions; links are UNVERIFIED, not necessarily dead.
- Phase 9 code has not been tested. npm dependency refresh fixed audit findings according to install output, but verify with `npm audit` and rerun test/build.
- No Git metadata means no commits or normal diff/status can be produced until project is initialized/cloned as a repository.

## 8. Important decisions / constraints

- User explicitly authorized implementation of all features in the supplied project plan. The plan says to preserve guest use, validate on the server, use owner identity only from verified auth, preserve data, use versioned migrations, provide tests/evidence, and never change the matcher formula.
- Phase 9 specifically requires asking the user how generated `data/processed/` reaches a clean clone **before** building Dockerfiles/full compose, and forbids committing data before its license is checked. Obtain that choice first.
- User’s urgent latest request superseded implementation/debugging: create only this handover and stop. Do no additional work in this turn.

## 9. Remaining roadmap (priority order)

1. Install/start Docker Desktop; run Compose config/build/up with locally generated `data/processed/` and verify backend health and Flyway with `AUTH_ENABLED=true`.
2. Install Playwright Chromium and execute the full browser E2E journey. Fix any proven failures.
3. Run OWASP Dependency-Check (configure GitHub Actions `NVD_API_KEY`) and execute CI on a Git-enabled remote checkout.
4. Confirm the generated-data mount works on a second clean clone after the user regenerates/provides the ignored processed directory; do not commit data.
5. Review all phase reports and update this handover with container/E2E/CI evidence. No Git metadata is currently present for committing.

## 10. Continuation instructions for the next Codex account

First inspect `HANDOVER.md`, `reports/PHASE9_2026-10-27.md`, `README.md`, Dockerfiles/Compose, and the Playwright config/spec. User has chosen a read-only mount of locally generated processed data. Do not read or echo `.env` secrets; use `.env.example` for variable names. Docker CLI and Chromium were unavailable during the last check, so run the container/E2E verification steps above first. Git metadata is absent; do not claim a commit or GitHub CI run.
