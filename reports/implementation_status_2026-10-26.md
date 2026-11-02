# Implementation Status — 2026-10-26

## Verified complete

- Existing Python pipeline and career matcher preserved; all 81 existing tests pass.
- Existing `backend/schema.sql` contains 26 `CREATE TABLE` definitions, and a structural scan found all foreign keys refer to tables already defined. It seeds `onet_occupations` and `onet_skill_elements`, with no inline mapping rows. It has not been applied to a database. The 26 includes the two optional interview tables; the header says 24 core tables. The approved schema v2 document was not available here to confirm that is the intended split.
- Existing O*NET loader now has a database-free `--dry-run` that validates both CSVs and reports row counts and skill IDs. Verified source files contain 1,016 metadata rows, 50,261 mapping rows, and 68 unique skill IDs.
- Spring Boot source now exposes `GET /api/skills` and `POST /api/matches`, validates ratings and RIASEC inputs, and calls `data_processing.matching_engine.CareerMatcher` through a JSON subprocess bridge. It returns career recommendations and per-career skill gaps directly from the existing engine.
- React source now has a skill assessment, optional RIASEC selection, ranked recommendations, match scores, and prioritized skill gaps. It requests actual skill and match data from the API; it has no fabricated career result set. The UI test uses a controlled fetch fixture; the separate backend MockMvc test exercises the real matcher.
- Backend Maven tests pass: 2 tests, including a MockMvc flow through the real Python matcher and validation for out-of-range ratings.
- Frontend Vite production build passes; Vitest passes 1 assessment-to-results UI flow test.

## UI refinement — later on 2026-10-26

- Replaced the 68-rating form with a three-step quick profile: optional name/study area plus career stage, one to three work interests, and six core O*NET skill ratings.
- Added a quick-profile scoring mode that normalizes only over skills the student rated; unrated dimensions are not counted as zero. The existing full matcher mode remains the default for existing callers.
- Added `frontend/public/skills.json`, generated from the processed O*NET data by `frontend/scripts/export_skills.py`. The skill catalog remains available when the API is offline; career recommendations are never fabricated.
- Replaced the raw fetch error with a clear service status and friendly explanation while keeping the profile on the page.
- Removed remote font loading so the page renders without external network requests.
- Frontend verification after this update: production build passes and 2 Vitest cases pass (quick profile to API results and offline catalog fallback). Python suite: 82 passed. Backend Maven suite: 2 passed, including the real matcher in quick-profile mode.

## Local service connection

- The backend now defaults to `127.0.0.1:8091`; Vite proxies `/api` to that port. This avoids the previously occupied 8080 and local CORS mismatch.
- Added `start_backend.ps1` to set the project and Python paths and launch Spring Boot. The offline notice now exposes that command.
- The matching API still could not be run as a live listener from the agent environment because local socket initialization is restricted there. Backend MockMvc tests pass, but the browser-to-live-server request remains unverified in this environment.

## Not verified or blocked

- MySQL Server 8.0.46 is installed, but Windows service `MySQL80` is STOPPED. No schema or data was written to a database.
- The Spring Boot process did not start as a live HTTP server in this environment (port 8080 was occupied; the retry on 8091 failed during local socket startup). The real request workflow is verified with MockMvc instead.
- MySQL persistence is not connected to the MVP API; the assessment is processed in memory by the existing matcher. The loader is implemented, but was not run against a live database.
- The project folder has no `.git` directory, so Git status and diffs are unavailable.
- Approved schema v2, implementation plan v2, algorithm notes, and risk list were not found. The available schema therefore cannot be certified against those sources.

## Run commands

From the project root, after installing declared frontend dependencies and starting MySQL yourself if desired:

```powershell
.\venv\Scripts\python.exe -m pytest tests/ -q --basetemp .pytest-workspace-temp
.\venv\Scripts\python.exe backend\load_onet_data.py --dry-run
```

Backend (from `backend/`, set `PROJECT_ROOT` to the project root if needed):

```powershell
$env:PROJECT_ROOT = (Resolve-Path ..).Path
$env:PYTHON_EXECUTABLE = (Resolve-Path ..\venv\Scripts\python.exe).Path
$env:SERVER_PORT = '8091'
$env:SERVER_ADDRESS = '127.0.0.1'
& 'C:\Program Files\Maven\apache-maven-3.10.0\bin\mvn.cmd' test
& 'C:\Program Files\Maven\apache-maven-3.10.0\bin\mvn.cmd' spring-boot:run
```

Frontend:

```powershell
cd frontend
npm.cmd run build
npm.cmd test
npm.cmd run dev
```

The frontend dependencies were installed after approval and are captured in `frontend/package-lock.json`.

## Career workspace expansion — 2026-10-27

- Added a persistent workspace sidebar with Overview, Career match, My roadmap, Interview studio, Saved careers, and Settings sections.
- Added Overview dashboard cards for profile readiness, matches, saved roles, roadmap progress, and direct entry points to each section.
- Added an interactive roadmap with checkable next steps. When match results exist, first steps are derived from the strongest role's actual skill-gap data; otherwise the page shows general profile and exploration steps.
- Added an interview prompt studio with rotating reflection prompts and a private, session-only answer textarea.
- Added a Saved careers shortlist with per-role save/remove controls persisted in browser local storage.
- Added Light, Dark, and System appearance settings. Theme selection, saved roles, and roadmap completion persist in this browser.
- Applied a responsive sidebar and multi-column layout, and added dark theme styling.
- Frontend production build passes after the workspace update. UI test suite was not rerun after the workspace update.

