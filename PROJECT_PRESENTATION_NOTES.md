# Major Project Presentation–2 Notes

## 1. Project overview

- **Title:** AI-Powered Personalised Career Guidance and Skill Gap Analysis Platform (Pathfinder).
- **Problem:** Students may not know which occupations fit their current skills, interests, and learning needs.
- **Solution:** A web platform compares a student's self-reported profile with occupational skill data, explains role matches and gaps, and offers career-planning tools.
- **Objectives:** Give explainable career matches; show skill gaps; support role exploration, learning plans, resume review, interview practice, job comparison, and application tracking.
- **Applications:** Student career exploration, early-career planning, portfolio/learning planning, and personal job-search tracking. It is guidance, not hiring or clinical advice.

## 2. Technology stack

| Area | Actually used/configured |
|---|---|
| Frontend | React 19, JavaScript, Vite 7, React Router 7, CSS; Vitest/Testing Library; Playwright configured for E2E |
| Backend/API | Java 24, Spring Boot 3.5.5, REST, Spring Security, JDBC, Bean Validation, SpringDoc OpenAPI |
| Matching/data pipeline | Python, pandas, NumPy, openpyxl; Matplotlib/Seaborn/Plotly for analysis/visualisation |
| Database | MySQL 8.4 in Compose; Flyway SQL migrations V1–V7; H2 for backend tests. JDBC repositories are used; no JPA entity layer was found. |
| Career data | O*NET 31.0 Excel and ESCO v1.2.1 CSV files are present under `data/raw/`; cleaned/generated CSVs are present under `data/processed/`. O*NET is currently used for scoring; ESCO is supplementary, not scored. |
| AI/LLM | Optional server-side OpenAI-compatible Chat Completions adapter (`CAREER_AI_*`); used for mentor and interview feedback. Blank key/failure uses local fallback. Career match scores are deterministic, not LLM-generated. |
| Other | Docker Compose, MySQL container, nginx static frontend, Maven, Node/npm, Python virtual environment, OpenAPI/Swagger |
| Development assistant | OpenAI Codex is confirmed by this project-work context. VS Code, Antigravity, and other assistants/tools are **not verified** from project evidence. |

## 3. Development workflow

| Stage | What / tool | Why; alternative considered |
|---|---|---|
| 1. Source data | O*NET/ESCO files inspected with Python/pandas scripts | Occupational data is more structured than manually writing role requirements; job-posting APIs would need access, licensing, and maintenance. |
| 2. Prepare dataset | Python cleans fields, normalizes aliases, flags uncertain mappings, and builds CSV maps | Keeps reproducible transformations and source IDs; a manual spreadsheet process would be harder to repeat. |
| 3. Score matches | Python matching engine; Spring calls it through a subprocess bridge | Preserves the existing tested Python formula while Java handles API/security; rewriting it in Java would duplicate logic. |
| 4. Serve features | Spring Boot REST controllers/services and JDBC | Reuses Java backend/security and exposes documented endpoints; a Python API was possible but would require another backend boundary. |
| 5. Present features | React/Vite workspace and reusable components | Supports routed interactive screens; server-rendered pages were a possible simpler alternative but less suited to this interactive workspace. |
| 6. Persist accounts | MySQL + Flyway, enabled by environment settings; guests use browser storage | Relational ownership and versioned schema for signed-in data; guest-only mode avoids requiring a database. |
| 7. Package/demo | Docker Compose for MySQL, API, and nginx frontend; PowerShell scripts/docs | Reproducible local stack; running services separately remains available for development. |

## 4. Implementation status

| Area | Status and evidence-based note |
|---|---|
| Dataset | **Present and pipeline implemented.** 46 O*NET and 19 ESCO raw files are in the folder; processed CSVs include the career map and metadata. Raw/processed data is git-ignored, so a clean clone needs local data. |
| Preprocessing | **Implemented.** Inspection, cleaning, alias normalization/review flags, and map-building scripts exist. ESCO is prepared but not used by current matcher scoring. |
| Database/schema | **Implemented for account mode.** Flyway V1–V7 define auth, profiles, sessions, assessment, resume/gap, career tools, applications, and admin tables. Live DB state is **not verified here**. `backend/schema.sql` is a separate older design; current app startup uses Flyway, not that legacy script. |
| Backend/frontend | **Implemented in source.** REST API and routed React Career Cockpit screens cover matching and multiple career tools. Current live browser behavior is **not verified in this inspection**. |
| Auth/profile | **Implemented, opt-in.** Public registration creates USER accounts; JWT access and rotating HTTP-only refresh cookies; profile/assessment/target-role APIs. Admin is seeded only from server environment variables. |
| Career matching | **Implemented.** O*NET importance/level weighted skill coverage plus small capped RIASEC bonus; returns ranked roles, strengths, and prioritized gaps. Scores are profile similarity, not employment probabilities. |
| Skill-gap/roadmap/projects | **Implemented.** Gap statuses persist for users; resource-backed roadmap and role-aware projects use local curated data and account records. |
| Resume analyzer | **Implemented with heuristics.** PDF text extraction, contact/section/skill detection and heuristic ATS-style report; scanned PDFs without selectable text need OCR, which is not implemented. Not an employer ATS guarantee. |
| Mentor/interview | **Implemented with fallback.** Saved mentor conversations and mock interview questions/feedback; optional LLM provider, local rubric otherwise. Voice speech recognition depends on the browser and is not verified here. |
| Job matcher/applications | **Implemented.** Job description comparison is local/deterministic; applications support CRUD and status/follow-up tracking. |
| Admin moderation | **Implemented manually.** Admin can review users, suspend/reactivate/archive, and see audit logs. Automatic fake-profile detection/scoring is **not implemented**. Admin Content API/table exist, but Feedback and Content screens were removed from current frontend routes/navigation. |
| Testing/deployment | **Tests and CI/E2E config exist.** Reports record Python (87), frontend (32), backend (23) tests/builds passing at Phase 9/10 checkpoints. These were not rerun for this document. Docker startup and browser E2E are recorded as unverified; current containers are **not verified**. |

## 5. System workflow

```text
Student profile + skill ratings + interests
        → React workspace sends request to Spring REST API
        → Spring validates/rate-limits request and calls Python matcher
        → Python compares profile with generated O*NET occupation-skill CSVs
        → API returns ranked roles, match explanation, strengths, and skill gaps
        → UI shows results and suggested roadmap/resources
        → If signed in, profile/results/progress are stored in MySQL; guest data stays in browser storage
```

Resume, interview, mentor, job-matcher, and application flows use their respective APIs/services. Optional LLM calls are server-side; career scoring and job-description matching remain local/deterministic.

## 6. Architecture and database

- **Modules:** `data_processing/` → generated CSVs → Python matcher → Java `MatcherBridge`/Spring services and controllers → React routes/screens.
- **Important files:** `data_processing/matching_engine.py`; `backend/src/main/java/edu/careerguidance/`; `frontend/src/pages/WorkspacePage.jsx`; `frontend/src/components/`; `frontend/src/api/`.
- **Main Flyway tables:** `users` → one `student_profiles` record per user; `refresh_tokens`; `match_sessions` → `match_results`; `saved_careers`; `roadmap_items`; `career_assessments`; `resume_analyses`; `skill_gap_progress`; `career_tool_records` (roadmap, project, mentor, interview, job-description JSON records); `job_applications`; admin notes, audit logs, and content items.
- User-owned rows reference `users.id` and generally cascade on account deletion. Profile and several flexible records store JSON/text payloads. Migration history is V1–V7. Current Flyway migrations, not the older `backend/schema.sql`, are the runtime account schema.

## 7. Algorithms and logic

- Map a self-rating **1–5** to O*NET level **0–7**: `level = (rating − 1) × (7 / 4)`.
- For each required skill, coverage is `min(user level, required level) / required level`; multiply by O*NET importance and compute the weighted average × 100.
- Add a capped RIASEC interest-overlap bonus (configured at 3 points per overlap, maximum 5); rank roles and return the top results.
- Gap priority is `(required level − user level) × importance`; highest-priority gaps appear first. This is a transparent heuristic, not a trained predictive model.
- Preprocessing preserves source identifiers, applies an editable skill alias map, keeps Java distinct from JavaScript, and flags uncertain aliases for review.
- Resume score is a rule-based completeness/keyword heuristic. Job-description fit is a local comparison against saved skills, resume extraction, and role requirements. No measured model accuracy is claimed.

## 8. Demo guide

**Prerequisites:** Windows PowerShell, Python/venv dependencies, Java 24, Maven, Node.js/npm; for Compose, Docker Desktop; generated `data/processed/`; `.env` configured privately. MySQL/Flyway and auth require the auth/database environment variables. AI key is optional. Never show `.env` or secrets during presentation.

**Compose demo (from project root):**

```powershell
mvn -B -f backend/pom.xml -DskipTests package
docker compose up --build -d
docker compose ps
```

Open `http://localhost:8081`; API health: `http://127.0.0.1:8091/api/health`; API docs: `http://localhost:8091/swagger-ui/index.html`. Sign in at `/login` or `/admin/login` only if account mode and admin seed are configured. To stop without deleting saved DB data: `docker compose down` (**do not** add `-v`).

**Suggested walk-through:** create a short profile → rate skills/choose interests → explain top career match and gaps → open role details/compare → show roadmap/resource and what-if comparison → if time, demonstrate resume review and job tracker. Use synthetic/demo data only.

**Known setup/demo risks:** Compose requires the local processed-data mount. A clean clone will not contain the ignored datasets/processed outputs. AI key may be blank, so show fallback behavior. Live MySQL persistence, full Docker acceptance and E2E are not verified in latest phase reports. Resource link reachability was not verified because of network limits. Resume OCR, automatic fake-account detection, and account email/password-reset flows are not implemented.

## 9. Likely faculty questions

1. **What problem are you solving?** Students need a clearer, evidence-based way to connect their current skills and interests with possible careers and learning gaps.
2. **What is the main contribution?** An explainable end-to-end workflow joins occupational data, skill matching, gaps, and practical career-planning tools.
3. **Which dataset is used?** O*NET 31.0 drives current matching; ESCO v1.2.1 is cleaned and available as supplementary data but is not used in match scoring yet.
4. **How is a match score calculated?** It is the importance-weighted coverage of a role’s required skills, with a small capped RIASEC interest bonus.
5. **Is 80% a chance of getting hired?** No. It measures similarity to the selected O*NET requirements based on self-reported skill ratings.
6. **Is this really AI?** The core recommender is deterministic and explainable. Optional server-side OpenAI-compatible generation supports mentor/interview feedback; a local fallback works without a key.
7. **Why Java and Python together?** Python already owns the data pipeline and matcher; Spring Boot provides API, validation, security, and persistence without rewriting the matcher.
8. **Why MySQL and Flyway?** MySQL stores account-owned data; Flyway applies versioned schema changes. Guests can use browser storage without accounts.
9. **How is privacy handled?** AI credentials remain server-side. Job descriptions are compared locally; account data is scoped to the signed-in user. Do not claim an independent security audit.
10. **How was it tested?** Automated Python, Java, and frontend tests are present; phase reports record passing suites. Full Docker/browser E2E remains unverified and should be described honestly.
11. **What are the limitations?** Self-rated skills, approximate cross-dataset terms, heuristic resume scoring, local generated-data setup, optional AI, and no OCR or automated suspicious-account detector.
12. **What would you do next?** Re-run full Docker/E2E and MySQL persistence checks; improve evaluation with a labeled validation set; add fair, privacy-conscious account-abuse review and OCR only after requirements and safeguards are defined.

## 10. Team preparation (four members)

| Member | Suggested topic |
|---|---|
| 1 | Problem, objectives, user journey, and O*NET/ESCO dataset sources/preprocessing |
| 2 | Architecture, React frontend, Spring APIs, authentication, and database design |
| 3 | Matching formula, RIASEC bonus, skill gaps, resume heuristic, and limitations |
| 4 | Roadmap/projects, mentor/interview/job tools, demo, testing evidence, and future work |

Each member should be ready to explain the score caveat, AI fallback, local data requirement, and unverified deployment/E2E status.
