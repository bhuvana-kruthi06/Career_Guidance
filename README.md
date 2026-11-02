# AI-Powered Personalised Career Guidance and Skill Gap Analysis Platform

> **Major Project** | Department of CSE | Supervisor: Dr. Ms. N. JAYASHREE

## Project Overview

This platform recommends suitable career paths based on a student's skills and interests,
calculates career-match scores, identifies missing skills, and suggests personalized
learning roadmaps. It integrates O\*NET and ESCO occupational datasets with a weighted
skill-matching engine and (in later phases) an LLM API for personalized guidance.

The repository contains the data-processing pipeline, a local career-matching API, a React career workspace, optional authenticated accounts, and versioned persistence for signed-in users. Guest matching remains available with account support disabled by default.

The workspace uses URL routes for Overview (`/overview`), Career match (`/match`), match results (`/results/:id`), Career explorer (`/careers`), career details (`/careers/:socCode`), compare (`/compare`), Roadmap (`/roadmap`), Interview studio (`/interview`), Saved careers (`/saved`), Settings (`/settings`), Sign in (`/login`), Register (`/register`), and Account (`/account`). Guest profiles and preferences stay in browser storage. Signed-in profiles, match sessions, saved careers, and roadmap progress are stored in MySQL.

The Admin Console is available at `/admin/login` after configuring `ADMIN_EMAIL`, `ADMIN_PASSWORD`, and `ADMIN_NAME` in the ignored `.env` file. See [`docs/ADMIN_CONSOLE.md`](docs/ADMIN_CONSOLE.md) for roles, admin routes, audit behavior, setup, and manual verification. Admin content records are currently an extension registry; static career, project, assessment, and resource sources remain unchanged.

Backend API docs (with auth disabled or enabled) are available at `http://localhost:8091/swagger-ui/index.html`; the OpenAPI JSON is at `/v3/api-docs`. See [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md), the [decision records](docs/adr/), and [current limitations](docs/LIMITATIONS.md).

---

## Technology Stack

| Layer         | Current implementation                                      |
|---------------|-------------------------------------------------------------|
| Data Science  | Python 3.15, pandas, NumPy, Matplotlib, Seaborn             |
| Backend       | Java 24, Spring Boot 3.5.5 REST API                         |
| Matcher       | Python matcher invoked by the backend as a subprocess       |
| Frontend      | React 19, Vite 7, JavaScript, CSS                           |
| Database      | MySQL 8 for opt-in authentication and account persistence; Flyway migrations |
| AI / LLM      | Optional server-side OpenAI-compatible provider for mentor and interview feedback, with local fallbacks |
| Authentication | Optional email/password accounts, JWT access tokens, rotating refresh cookies |

## Quick Start (Windows)

This MVP matches careers from generated local CSV files. MySQL is not needed to run the profile and matching flow. A fresh clone does not include the O*NET/ESCO raw datasets or generated `data/processed/` files; download the licensed source datasets from the official sources below before running the data pipeline. At minimum, the cleaning pipeline expects these filenames in `data/raw/`:

- O*NET: `Occupation Data.xlsx`, `Essential Skills.xlsx`, `Transferable Skills.xlsx`, `Knowledge.xlsx`, `Abilities.xlsx`, `Software Skills.xlsx`, `Education.xlsx`, `Job Zones.xlsx`, `Career Interest Types.xlsx`, and `Related Occupations.xlsx`.
- ESCO: `occupations_en.csv`, `skills_en.csv`, `occupationSkillRelations_en.csv`, `skillGroups_en.csv`, and `ISCOGroups_en.csv`.

### Prerequisites

- Python 3.15
- Java 24 and Maven (the `mvn` command must be on PATH, unless `MAVEN_CMD` points to Maven)
- Node.js and npm
- O*NET 31.0 source files and ESCO v1.2.1 CSV files, placed in `data/raw/onet/` and `data/raw/esco/`

### Prepare the local data

Run these commands from the project root in PowerShell:

```powershell
python -m venv venv
.\venv\Scripts\python.exe -m pip install -r requirements.txt
.\venv\Scripts\python.exe data_processing\inspect_data.py
.\venv\Scripts\python.exe data_processing\clean_data.py
.\venv\Scripts\python.exe data_processing\normalize_skills.py
.\venv\Scripts\python.exe data_processing\build_career_skill_map.py
.\venv\Scripts\python.exe frontend\scripts\export_skills.py
```

The O*NET skill map and career metadata are generated under `data/processed/`. These generated files are git-ignored and must be regenerated on a fresh clone. Keep the original source files under `data/raw/`; the cleaning scripts read them and write derived files to `data/processed/`.

### Start the app

Open one PowerShell window at the project root and start the matching API:

```powershell
.\start_backend.ps1
```

Open a second PowerShell window and start the frontend:

```powershell
Set-Location frontend
npm ci
npm run dev
```

Open the local URL printed by Vite (normally `http://localhost:5173`). Keep both terminals open. The frontend proxies `/api` requests to `http://127.0.0.1:8091`.

The API exposes `GET /api/health`, which reports whether the Python skill catalog is ready. Match requests are limited to 20 per client IP per minute, 16 KB per request, 100 skill ratings and 25 results; at most four matching subprocesses run at once. These limits can be set with `MATCH_RATE_LIMIT_CAPACITY`, `MATCH_MAX_BODY_BYTES`, and `MATCH_MAX_CONCURRENT`. The `dev` Spring profile allows the local Vite origins. For deployment, set `SPRING_PROFILES_ACTIVE=prod` and provide the exact trusted site origins in `CORS_ORIGINS`; an empty production origin list blocks cross-origin browser access.

### Run the complete stack with Docker Compose

Docker Compose starts MySQL, the Spring/Python API, and the static React site. It deliberately mounts generated local files instead of copying licensed dataset derivatives into images. First generate `data/processed/` using [Prepare the local data](#prepare-the-local-data), install Docker Desktop, copy `.env.example` to `.env`, and replace the database password placeholders with private local values. Build the backend JAR with Java 24 and Maven, then build and start the Docker stack from the repository root:

```powershell
Copy-Item .env.example .env
# Edit .env: set unique local MYSQL_ROOT_PASSWORD and DATABASE_PASSWORD values.
mvn -B -f backend/pom.xml -DskipTests package
docker compose up --build
```

Open `http://localhost:8081`. The API is available at `http://127.0.0.1:8091`; health requires the mounted matcher data. The MySQL port stays internal to the Compose network. The sample `.env.example` enables account-backed profiles and assessments. Before starting, replace its database password placeholders and `JWT_SECRET` with private local values (`JWT_SECRET` must be at least 32 bytes). Set `AUTH_ENABLED=false` and `VITE_AUTH_ENABLED=false` only for guest-only mode; preserve `AUTH_COOKIE_SECURE=false` for local HTTP. For HTTPS deployment, enable secure cookies and configure a trusted TLS proxy and CORS origins. Set `PROCESSED_DATA_DIR` in `.env` if your generated directory is outside the repository. The mount is read-only. Stop with `Ctrl+C`; `docker compose down` removes containers, and `docker compose down -v` also deletes the persistent MySQL volume.

### Optional account and persistence setup

Guest matching works without MySQL. To enable accounts and store profiles, sessions, saved careers, and roadmap progress:

1. Copy `.env.example` to `.env`, replace both MySQL password placeholders with local values, then start the database:

   ```powershell
   docker compose up -d mysql
   ```

2. In the PowerShell window that will run the backend, set `AUTH_ENABLED=true`, `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` to the same database settings as `.env`. Generate a private JWT secret for that shell:

   ```powershell
   $env:AUTH_ENABLED = 'true'
   $env:DATABASE_URL = 'jdbc:mysql://127.0.0.1:3306/ai_career_guidance?useSSL=false&serverTimezone=UTC'
   $env:DATABASE_USERNAME = 'career_guidance'
   $env:DATABASE_PASSWORD = 'the same local database password configured in .env'
   $env:JWT_SECRET = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
   ```

   `AUTH_ENABLED=true` runs the Flyway migrations on backend startup. For local HTTP, keep `AUTH_COOKIE_SECURE=false`; production HTTPS must set it to `true` and use a private secret from the deployment environment.

3. In the frontend PowerShell window, enable the account screens before starting Vite:

   ```powershell
   $env:VITE_AUTH_ENABLED = 'true'
   npm run dev
   ```

The generated local data must exist for career search and matching. See [Prepare the local data](#prepare-the-local-data). `scripts/check_links.py` checks the HTTPS resource URLs in the curated skill catalog.

### Current feature set

- A short profile, interest selection, O*NET skill ratings, match explanations, and a what-if comparison that reruns the existing matcher.
- Career search, RIASEC filtering, occupation skill requirements, saved-career comparison (up to three), and a gap-ranked roadmap with curated learning resources.
- Account registration/sign-in when enabled, match history and trend chart, per-user saved careers and roadmap progress, one-time local-data import, and account-data deletion.
- Account-backed multi-phase learning roadmap, role-aware portfolio project recommendations, and saved AI mentor conversations.
- Mock interview sessions with role/type/difficulty selection, per-answer feedback, saved readiness reports, and dashboard readiness metrics.
- Job description skill matching with saved history and an action to add job-specific learning tasks to the roadmap.
- Job application Kanban with CRUD, status moves, search/filtering, status analytics, and due follow-ups on the dashboard.
- Guest preferences remain local. The matcher formula in `data_processing/matching_engine.py` is documented in [`docs/MATCHING.md`](docs/MATCHING.md) and has not changed.

See [`docs/CAREER_TOOLS.md`](docs/CAREER_TOOLS.md) for the authenticated API routes and record storage details. Job description text is analyzed locally and is not sent to an external AI provider. Interview answers may be sent to the configured server-side provider for feedback; without a configured key the local scoring rubric is used.

### Run checks

```powershell
.\venv\Scripts\python.exe -m pytest --basetemp .pytest-workspace-temp
$maven = if ($env:MAVEN_CMD) { $env:MAVEN_CMD } else { 'mvn' }
& $maven -f backend\pom.xml test
Set-Location frontend
npm ci
npm test -- --run
npm run build
npm run test:e2e
```

The Playwright end-to-end test exercises profile creation, real career matching, registration, sign-out/sign-in, saving a career, and restoring the saved list after refresh. It requires the Docker stack with `AUTH_ENABLED=true`, `VITE_AUTH_ENABLED=true`, generated data, and browser binaries installed once with `npx playwright install chromium`. Set `PLAYWRIGHT_BASE_URL=http://127.0.0.1:8081`, then run `npm run test:e2e` from `frontend/`. Run `scripts/demo.ps1` for a guided walkthrough. CI runs the behavioral Python tests (dataset path-presence tests are excluded because raw/generated files are intentionally git-ignored), backend tests, frontend tests/build, `npm audit`, and OWASP Maven dependency check. E2E remains a local command because datasets are not distributed through CI. Set the optional GitHub Actions secret `NVD_API_KEY` to avoid public NVD feed rate limits during the OWASP scan.

If Maven is not on PATH, set `MAVEN_CMD` to the full path of `mvn.cmd` before running the backend startup script or Maven tests.

---

## Dataset Sources & Attribution

### O\*NET 31.0 Database
- **Source:** U.S. Department of Labor / Employment and Training Administration (USDOL/ETA)
- **URL:** https://www.onetcenter.org/database.html
- **License:** Creative Commons Attribution 4.0 International (CC BY 4.0)
- **Release:** August 2026
- **Location in project:** `data/raw/onet/`
- **Attribution required:** Yes — cite O\*NET when publishing results

### ESCO v1.2.1
- **Source:** European Commission
- **URL:** https://esco.ec.europa.eu/en/use-esco/download
- **License:** Creative Commons Attribution 4.0 International (CC BY 4.0)
- **Version:** v1.2.1 (December 2025)
- **Location in project:** `data/raw/esco/`
- **Attribution required:** Yes — cite ESCO when publishing results

> **Important:** Raw dataset files are excluded from version control (see `.gitignore`).
> Download them from the official sources above and place them in the paths shown.

---

## Project Structure

```
AI-Career-Guidance/
├── AI_Personalised_Career_Guidance_Phase_I.pptx  ← Phase-I design reference
├── data/
│   ├── raw/
│   │   ├── onet/          ← O*NET 31.0 .xlsx files (46 files)
│   │   └── esco/          ← ESCO v1.2.1 .csv files (19 files)
│   ├── processed/         ← Cleaned & merged datasets (generated)
│   └── sample/            ← Small synthetic/sample files for testing
├── data_processing/
│   ├── inspect_data.py    ← Step 1: Inspect raw datasets
│   ├── clean_data.py      ← Step 2: Clean and standardise
│   ├── normalize_skills.py← Step 3: Normalise skill names
│   └── explore_data.py    ← Step 4: EDA and charts
├── reports/
│   ├── charts/            ← Generated EDA charts (PNG/HTML)
│   ├── data_analysis_report.md
│   ├── implementation_status.md
│   └── feature_enhancement_recommendations.md
├── tests/
│   └── test_pipeline.py   ← Automated tests
├── docs/                  ← Additional documentation
├── requirements.txt
├── .gitignore
└── README.md
```

---

## Setup Instructions (Windows)

### 1. Check Python version
```powershell
python --version
# Expected: Python 3.15.x
```

### 2. Create and activate a virtual environment
```powershell
cd "c:\Users\Bhuvana Kruthi\OneDrive\Documents\AI-Career-Guidance"
python -m venv venv
venv\Scripts\activate
```

### 3. Install dependencies
```powershell
pip install -r requirements.txt
```

---

## Running the Pipeline

Run each script in order from the project root with the virtual environment active. The career-skill map and frontend fallback catalog are required by the matching app; EDA is optional:

```powershell
# Step 1 — Inspect raw datasets (read-only, no changes)
python data_processing/inspect_data.py

# Step 2 — Clean data (outputs to data/processed/)
python data_processing/clean_data.py

# Step 3 — Normalise skill names
python data_processing/normalize_skills.py

# Step 4 — Build the O*NET map and metadata used by the matcher
python data_processing/build_career_skill_map.py

# Step 5 — Generate the frontend's offline skill catalog
python frontend/scripts/export_skills.py

# Optional — Exploratory data analysis (outputs to reports/)
python data_processing/explore_data.py
```

### Run tests
```powershell
pytest tests/ -v
```

---

## Normalization Rules

Skill names are normalized using a documented alias map in `data_processing/normalize_skills.py`.

**Examples:**
| Raw Name                     | Normalized Name |
|------------------------------|----------------|
| JS                           | JavaScript      |
| Javascript                   | JavaScript      |
| Structured Query Language    | SQL             |
| MS Excel                     | Microsoft Excel |

**Rules:**
- Original skill names and IDs are always preserved in a separate column (`skill_name_original`)
- Java and JavaScript are **never merged** — they have distinct O\*NET and ESCO identifiers
- Uncertain mappings are flagged with `normalization_flag = REVIEW_NEEDED`
- The alias map is editable without changing any other code

---

## Assumptions & Limitations

1. **Proficiency scale:** Students rate skills on 1–5 (1=Beginner, 5=Expert). O\*NET uses a 0–7 scale internally; mapping is documented in `normalize_skills.py`.
2. **Match scores are not probabilities:** A career-match score (e.g., 67%) reflects weighted skill overlap only. It does not predict employment likelihood or career success.
3. **Self-reported skills:** All student skill ratings are self-reported and not externally verified.
4. **ESCO–O\*NET overlap:** The two datasets are not officially aligned. Cross-dataset skill mapping is approximate and flagged where uncertain.
5. **Raw data is never modified:** All cleaning and normalisation outputs go to `data/processed/`. The `data/raw/` folder is read-only.

---

## Attribution

When presenting or publishing results from this project, include:

> This project uses data from:
> - O\*NET 31.0 Database by the U.S. Department of Labor (CC BY 4.0): https://www.onetcenter.org
> - ESCO v1.2.1 by the European Commission (CC BY 4.0): https://esco.ec.europa.eu

---

## Implementation Status

See [`reports/implementation_status_2026-10-26.md`](reports/implementation_status_2026-10-26.md) for the latest implementation notes and [`reports/BASELINE_2026-10-27.md`](reports/BASELINE_2026-10-27.md) for the Phase 1 pre-edit test baseline. These reports distinguish local checks from unverified live behavior.
