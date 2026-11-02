# Project Implementation and Feature Audit

**Project:** AI-Powered Personalised Career Guidance and Skill Gap Analysis Platform  
**Audit date:** 27 October 2026  
**Method:** Read-only inspection of project files. I did not change files, run tests, build the app, install dependencies, or start services for this audit.

**Status caveat:** “Implemented” means the relevant code exists. Runtime-dependent behavior is marked **UNVERIFIED** where I did not execute it. The project contains a dated status report with earlier test and build results; those are historical records, not fresh audit results.

## 1. Project overview

The project aims to recommend careers using skills and interests, rank those careers, and identify skill gaps. The README describes students as the main users; the current UI also offers early-career and career-change stages. That broader audience is inferred from the profile form.

- **Frontend:** React 19, Vite 7, JavaScript, CSS. A single React application contains the profile, results, dashboard, roadmap, interview prompt, saved-career, and settings screens. Navigation uses React state; there is no routing library.
- **Backend:** Spring Boot 3.5.5 REST API. Java validates requests and launches a Python process to run the matcher.
- **Matching and data processing:** Python with pandas and NumPy. O*NET CSV data feeds the matching engine; ESCO data is cleaned and analyzed but is not used for career scoring by the current matcher.
- **Database:** A MySQL 8 schema and Python data loader exist. The current API does not use the database: it reads processed CSV files through the Python matcher. No database connection was made during this audit.
- **Authentication:** Not implemented in the running app. The SQL schema describes users and tokens, but the backend has no login, registration, Spring Security, or authorization code.
- **External integrations:** O*NET and ESCO data are stored locally in the project. I found no live O*NET/ESCO API integration or LLM integration. The README describes LLM guidance as a later phase.
- **Deployment:** I found local startup configuration and a Windows backend script. I found no Docker, CI/CD, cloud-hosting, or production deployment configuration.

The top-level README still describes React, Spring Boot, MySQL, and security as later-phase work. Those statements no longer reflect all the code currently present.

## 2. File and folder inventory

| Location | Responsibility |
|---|---|
| README.md | Project description, data attribution, setup, and pipeline instructions. Some phase descriptions are stale. |
| frontend/src/App.jsx | All current frontend screens, state, profile flow, API requests, local saving, and theme selection. |
| frontend/src/styles.css | Main and workspace styles, responsive rules, and light/dark appearance. |
| frontend/src/main.jsx | React application entry point. |
| frontend/src/App.test.jsx | Two UI tests using mocked fetch responses. |
| frontend/package.json, package-lock.json, vite.config.js, index.html | Frontend scripts and dependencies, Vite API proxy, and HTML shell. |
| frontend/public/skills.json | Static skill-catalog fallback used when the API is unavailable. It does not contain career recommendations. |
| frontend/scripts/export_skills.py | Generates the fallback from the processed O*NET career-skill map. |
| backend/src/main/java/edu/careerguidance/api/MatchController.java, MatchRequest.java, ApiExceptionHandler.java | /api/skills and /api/matches, request validation, and service error responses. |
| backend/src/main/java/edu/careerguidance/service/MatcherBridge.java and MatcherUnavailableException.java | Starts the Python CLI and handles process output, timeouts, and failures. |
| backend/src/main/resources/application.yml, backend/pom.xml | API port, address and matcher configuration; Java/Spring dependencies. |
| backend/matcher_cli.py | JSON command-line bridge to the Python matcher and skill catalog. |
| backend/schema.sql | MySQL table definitions, constraints, indexes, and seed statements. |
| backend/load_onet_data.py | MySQL loader for occupation and occupation-skill data, including a no-database dry-run. |
| start_backend.ps1 | Windows script setting paths and starting Spring Boot. |
| data_processing/ | Inspection, cleaning, skill-name normalization, O*NET career-skill map generation, EDA, and matching engine. Principal files include clean_data.py, normalize_skills.py, build_career_skill_map.py, explore_data.py, and matching_engine.py. |
| data/raw/ | Local O*NET spreadsheet and ESCO CSV source files. |
| data/processed/ | Cleaned datasets, normalized skill files, career metadata, and the matching map. These files are locally present but excluded from Git by .gitignore. |
| tests/ | Python tests for pipeline utilities and the matching engine. |
| reports/ | Data analysis, inspection, and implementation-status documents. Some are historical. |
| requirements.txt, pytest.ini, .gitignore | Python dependencies, pytest configuration, and ignored files. |
| AI_Personalised_Career_Guidance_Phase_I.pptx | Phase I presentation/design reference. |

Generated or bulky content includes the Python venv, frontend node_modules, Vite dist, Maven target, processed/raw datasets, pytest cache files, and generated charts. These are not source modules. The README mentions data/sample/, but that folder was not present in the root inventory.

## 3. Feature inventory

| Feature | Description | Relevant files | Frontend status | Backend status | Database support | Verification status |
|---|---|---|---|---|---|---|
| Profile form | Optional name and study/work area; required career stage | App.jsx | Implemented | Context fields are not sent to the API | Profile table is schema-only | **PARTIALLY IMPLEMENTED** |
| Interest selection | Select one to three RIASEC work-interest categories | App.jsx, MatchController.java, matching_engine.py | Implemented | Validated and used as a score bonus | RIASEC tables exist but are unused by API | **IMPLEMENTED BUT NOT VERIFIED** |
| Skill ratings | Rate six selected O*NET skill areas from 1–5 | App.jsx, MatchRequest.java | Implemented | Validated; quick mode scores rated skills only | Student-skill table exists but is unused by API | **IMPLEMENTED BUT NOT VERIFIED** |
| Skill catalog | Fetch API catalog; fall back to static JSON | matcher_cli.py, App.jsx, skills.json | Implemented | Reads processed CSV | No runtime DB lookup | **IMPLEMENTED BUT NOT VERIFIED** |
| Career matching | Rank careers by weighted skill overlap and RIASEC bonus | matching_engine.py, matcher_cli.py, MatcherBridge.java | Results view exists | Matching logic and endpoint exist | Session/result tables are unused | **IMPLEMENTED BUT NOT VERIFIED** |
| Skill-gap display | Show gaps for skills in the profile’s assessed areas | matching_engine.py, App.jsx | Implemented | Gap calculation exists | Gap table is unused | **IMPLEMENTED BUT NOT VERIFIED** |
| Offline skill catalog | Continue the profile form without API connection | skills.json, App.jsx | Implemented | Not required for fallback | None | **IMPLEMENTED BUT NOT VERIFIED** |
| Overview dashboard | Profile, match, saved-career, and roadmap summary cards | App.jsx, styles.css | Implemented; values derive from in-memory state/local storage | None | None | **FRONTEND ONLY** |
| Personal roadmap | Checklist; with results, initial steps use top career’s gaps | App.jsx, styles.css | Implemented; progress stored locally | No learning-resource endpoint | Roadmap tables are unused | **FRONTEND ONLY** |
| Interview studio | Six rotating, hard-coded reflection prompts and a textarea | App.jsx | Implemented as a basic prompt screen | No interview API or answer analysis | Interview tables are unused | **MOCKED OR HARDCODED** |
| Saved careers | Save/remove matched roles in browser storage | App.jsx | Implemented locally | No server persistence | is_saved field is unused | **FRONTEND ONLY** |
| Theme setting | Light, dark, or OS system theme; saved in browser storage | App.jsx, styles.css | Implemented | None | None | **FRONTEND ONLY** |
| MySQL schema | User, assessment, match, guidance, roadmap, and interview table designs | schema.sql | No connected UI flow | No API database access | DDL exists; application usage is absent | **IMPLEMENTED BUT NOT VERIFIED** |
| O*NET database loader | Loads occupation and skill-map data; supports dry-run | load_onet_data.py | None | Loader code exists | Writes to MySQL if run without dry-run | **BACKEND ONLY** |
| Data cleaning and normalization | Reads local raw data and produces processed files | clean_data.py, normalize_skills.py | Indirectly supplies data | Offline Python scripts | None | **IMPLEMENTED BUT NOT VERIFIED** |
| ESCO/O*NET analysis | Dataset exploration and charts | explore_data.py, inspect_data.py | No analysis page | Offline scripts | None | **IMPLEMENTED BUT NOT VERIFIED** |
| RIASEC questionnaire | SQL question/answer design and seeded questions exist; UI uses category selection instead | schema.sql, App.jsx | Questionnaire is absent | No questionnaire endpoints/scoring | Schema and seed data only | **PARTIALLY IMPLEMENTED** |
| Authentication and roles | Tables describe users, roles, and token records | schema.sql only | No sign-in or registration | No authentication middleware or endpoints | Schema design only | **NOT IMPLEMENTED** |
| AI/LLM guidance | Mentioned as a later phase; conversation tables exist | README, schema.sql | No AI guidance screen | No LLM client or endpoint found | Schema design only | **NOT IMPLEMENTED** |
| Search, filters, compare, export | No current UI controls or matching endpoints for these flows | — | Missing | Missing | Some related data only | **NOT IMPLEMENTED** |

## 4. Frontend audit

### Screens and navigation

The React app has six state-selected sections: Overview, Career match, My roadmap, Interview studio, Saved careers, and Settings. Career match is the initial screen. The logo switches to Overview, and a header settings button opens Settings.

There is no URL router: section changes do not create navigable URLs or browser history entries. Refresh returns to the initial career-match screen. The profile, ratings, interests, and match results live in React state and are not persisted across refreshes.

### Forms and controls

The career flow has three steps:

1. Optional name and study/work area; required career stage.
2. Pick one to three interest cards.
3. Rate six named skills on a 1–5 scale.

The Continue buttons enforce step prerequisites, and ratings use aria-pressed to expose selection state. Name and study area are retained in frontend state but are not included in the match API payload. Career stage is also contextual only.

The result cards show match score, occupation details, and gaps. The score’s detailed base/interest breakdown is returned by the matcher, but the UI does not show that breakdown. There are no career search, filtering, sorting, pagination, comparison, or export controls.

### New workspace screens

- **Overview:** Summary cards and links to the app sections. Its counts reflect current in-memory results and browser-stored data, not a server account or historical activity.
- **Roadmap:** Checkable actions are persisted in local storage. Before a match, the actions are generic. After a match, up to five suggested actions are derived from the first result’s skill gaps; they are not learning resources, courses, or a sequenced curriculum.
- **Interview studio:** Cycles through six fixed prompts. The textarea is not submitted or saved. Leaving the section unmounts it, so draft notes are lost.
- **Saved careers:** Saves result objects to local storage, with controls to remove them. There is no account-level sync or dedicated compare view.
- **Settings:** Light, dark, and system appearance are saved in local storage. “Edit profile” returns to Career match; there are no broader profile-management, export, or clear-data controls.

### Loading, errors, accessibility, and layout

The app has a skill-catalog loading indicator, API connection status, offline fallback, reconnect control, and match-request error message. The “Reconnect” action retries GET /api/skills; it does not independently verify that match requests will succeed. No toast/notification system was found.

The UI uses responsive CSS rules and several accessibility affordances, including labels, button text, aria-label, aria-pressed, and a live status region. A full screen-reader, keyboard, contrast, and device-size audit was not performed. I found no dedicated accessibility test suite. Rating selection has theme-specific colors in CSS, but the latest visual change was not independently inspected in a browser for this audit.

The interview prompts and the six quick-profile skill names are hard-coded in the frontend. The profile uses actual O*NET skill data for the skill catalog and actual API results for careers; the career result cards themselves are not mock data in the application code.

## 5. Backend and API audit

### Endpoints

| Endpoint | Request/response | Behavior |
|---|---|---|
| GET /api/skills | Returns a list of skill IDs, names, and categories | Java starts matcher_cli.py, which reads the processed CSV. |
| POST /api/matches | Accepts studentSkills, studentInterests, topN, and assessedOnly; returns a ranked array of career results and skill gaps | Validates skill ratings, top-N, and interest count/codes, then invokes the Python matcher. |

Java request validation constrains ratings to 1–5 and top-N to 1–50. The controller requires at least three skills in assessed-only mode and one to three valid RIASEC codes. The UI currently submits six skills.

MatcherBridge starts a Python subprocess for each API call, writes JSON through stdin, and waits up to 90 seconds. It returns a service-unavailable error for matcher failures and logs backend errors. The Python engine reloads the processed career map and metadata when instantiated; the current bridge does this for each request, which may be slow under repeated or concurrent use.

### Missing backend functionality

There are no API routes for account management, profiles, saving careers, roadmap persistence, interview practice, AI guidance, or career transitions. No database repository/JDBC dependency is present in the Spring application. No API documentation, rate limiter, health endpoint, application-level audit trail, or role authorization was found.

CORS allows the configured origin (default http://127.0.0.1:5173) and http://localhost:5173. Spring defaults the API bind address to loopback. These settings suit local development; production origins and deployment behavior are not established.

## 6. Database audit

backend/schema.sql defines **26 tables**, although its header says “24 tables.” The final two tables are for mock interviews. The schema has primary keys and a number of foreign keys, unique constraints, indexes, enum fields, and CHECK constraints.

The tables cover:

- Users and token records.
- Student profiles and RIASEC questions/answers.
- O*NET skill elements, student ratings, and evidence.
- Occupations, career-skill mappings, match sessions/results/history, what-if scenarios, and skill gaps.
- AI conversations, messages, and feedback.
- Learning roadmaps, resources, items, milestones, and skill progress history.
- Career transitions and mock-interview questions/attempts.

The schema includes seed statements for O*NET skill elements and RIASEC questions. The onet_occupations insert appears only as a commented example; the Python loader is the implemented path for loading occupations. The loader also imports the career-skill map. An earlier implementation-status note says the schema seeds onet_occupations; that claim does not match the current SQL source.

No database connection, migration, or persisted application flow was verified. All described application tables besides the loader’s occupation and skill-map targets are unused by the current API.

Potential schema/design follow-ups based on code inspection:

- The header’s 24-table count conflicts with the 26 table definitions.
- Evidence verification/confidence behavior is described in comments but no trigger/service code was found to calculate it.
- Match-result uniqueness is not enforced for a career or rank within a session.
- No versioned migration tool or migration directory was found.
- The INSERT IGNORE loader is idempotent for duplicate keys but will not update existing occupation/map rows when source data changes.
- The loader commits each table separately; a failure partway through the full load could leave a partial import.

## 7. Authentication and security

- **Registration/login/password hashing:** Not implemented in the application. The schema comments describe BCrypt hashes, but no password-handling code or Spring Security dependency exists.
- **Sessions/tokens/roles:** Token and role tables exist only as schema design. No token issuance, validation, refresh, role checks, or protected routes were found.
- **Authorization:** No server-side access control exists. Current matching endpoints are unauthenticated.
- **Input validation:** Present for rating ranges, top-N, and RIASEC codes. The API does not accept the profile name or study area. There is no documented request-size limit or rate limiting.
- **SQL injection:** The current API does not issue SQL queries, so a SQL injection path was not found in the running API code. The MySQL loader uses parameterized inserts for values. This is not a full security assessment.
- **Command injection:** The Java bridge passes the configured Python executable and script as process arguments and sends user data through stdin rather than building a shell command. This reduces shell-injection exposure in the inspected path. The configured executable and project-root values still need deployment validation.
- **XSS/CSRF:** React renders ordinary text, and I found no unsafe raw-HTML rendering. CSRF protection is not configured; the current API has no cookie-based login flow. If browser sessions are added later, CSRF and session protections need to be designed.
- **Secrets:** .env exists and includes database configuration variable names. Secret values are not included here. The project’s .gitignore excludes .env; this audit did not verify whether any secrets are valid or whether the file is tracked elsewhere. The matching API does not read database credentials.
- **Dependencies:** No vulnerability scan was performed.

## 8. User flows

### Create a career match

1. User opens Career match and enters optional profile context plus required career stage.
2. User selects one to three interest categories.
3. User rates six skills and submits.
4. Frontend calls GET /api/skills on load and POST /api/matches on submit.
5. Spring validates input and starts the Python matcher; Python reads processed CSVs and calculates ranked results and gaps.
6. On success, React displays career cards and skill gaps. The user can save a career locally.
7. On API failure, the skill catalog can fall back to skills.json; the profile remains in the current page state and a service message is shown. Career matches cannot be produced by that fallback.

No database operations occur in this flow. The browser-to-live-backend flow was not verified during this audit.

### Save and revisit a career

The user saves a result card; the career object is written to local storage and appears in Saved careers. Removing it updates local storage. No API call or database operation is involved. Cross-device persistence is absent.

### Build a roadmap

The user opens My roadmap and checks off actions. Without match results, actions are generic; with results, actions derive from gaps in the top match. Completion state is local. There are no course/resource lookup calls or server-side roadmap records.

### Practice interview prompts

The user opens Interview studio, reads one of six hard-coded prompts, types notes, and advances to another prompt. There is no API call, evaluation, or persistence. Drafts are lost when the section unmounts.

### Change theme

The user chooses Light, Dark, or System in Settings. The preference is stored locally and applied by the frontend. There is no backend or database operation.

### Database import

The loader documents a MySQL import and includes a dry-run mode. The schema must be applied and MySQL must be reachable for a real import. The loader’s actual live database behavior and installed schema state are unverified here.

## 9. Testing and quality

Existing test files include Python tests for the processing pipeline and matching engine, a Spring MockMvc test for validation and an API-to-real-Python-matcher case, and two frontend Vitest cases for the quick-profile flow and offline catalog fallback.

I did not execute these tests or the production build for this audit. The dated status document records earlier results—82 Python tests, 2 backend tests, and 2 frontend tests passing before the recent workspace expansion. It also explicitly says UI tests were not rerun after that expansion. Those records are historical and are not a fresh result for the current source tree.

No end-to-end browser test, coverage report, lint/format script, CI workflow, or dependency vulnerability report was found. Maintainability concerns include the single large App.jsx, long compressed JSX lines, and an appended stylesheet with overlapping legacy and workspace rules. Some documentation describes earlier project phases and is no longer current.

## 10. Deployment and production readiness

- **Frontend:** Vite development server on port 5173; /api is proxied to 127.0.0.1:8091 by default. A production build script exists, but no hosting configuration was found.
- **Backend:** Spring Boot defaults to 127.0.0.1:8091; start_backend.ps1 sets the project root and Python executable. The script expects Maven at a fixed Windows path, which is machine-specific.
- **Database:** MySQL 8 schema and loader exist. There is no database service configuration, hosted database setup, backup policy, or verified schema deployment.
- **Configuration:** Backend uses SERVER_PORT, SERVER_ADDRESS, PROJECT_ROOT, PYTHON_EXECUTABLE, and CORS_ORIGIN. The database loader reads DB settings from .env or the environment. The root .env and the presence of database variable names were observed; values are omitted.
- **Operations:** No health check, structured request audit, metrics, monitoring, error-reporting service, backup/recovery guide, or deployment workflow was found.
- **Production readiness:** The app is a local-development prototype with a working-code path toward a match flow. Authentication, persistence, production hosting, and operational safeguards remain unimplemented or unverified.

## 11. Missing features and improvement recommendations

Effort estimates: **S** small, **M** medium, **L** large, **XL** extra large. These are planning estimates, not commitments.

### A. Essential bug fixes

| Recommendation | Why / affected modules | Work and complexity | Dependencies |
|---|---|---|---|
| Add end-to-end checks for browser → Spring → Python and error cases | The local proxy, CORS, executable path, CSV availability, and process timeout need to work together. | Frontend/backend tests; **M** | Stable local dev setup |
| Add robust malformed-storage handling | JSON.parse(localStorage...) can throw during app initialization if stored values are invalid. | Frontend; **S** | None |
| Preserve or clearly warn about interview drafts when navigating away | Current textarea is transient and disappears when the section unmounts. | Frontend; **S–M** | Product decision on privacy/persistence |
| Verify result and skill-catalog empty/error responses | The frontend has generic failure handling; API failure modes should map to actionable messages. | Frontend/backend; **M** | API error contract |

### B. Incomplete existing functionality

| Recommendation | Why / affected modules | Work and complexity | Dependencies |
|---|---|---|---|
| Persist profile, assessments, match sessions, and saved roles | Current profile/results are in memory and saved roles are browser-local only; users lose work on refresh or another device. | Frontend/backend/database; **L** | Authentication and schema integration |
| Turn roadmap items into real learning steps | Current suggestions are generic or skill-gap labels, not resources with providers, costs, duration, or links. | Frontend/backend/database/data; **L** | Resource model and curation |
| Expand interview studio beyond static prompts | Current experience lacks role-specific prompts, draft history, feedback, or practice tracking. | Frontend/backend; **M–L** | Product definition; optional AI service |
| Add search, filters, compare, and transparent score details | Users cannot narrow results or understand the score components in the UI. | Frontend/backend; **M** | API response and UX design |
| Reconcile the RIASEC quiz design with the current interest picker | The schema has question-level answers; the UI simply chooses categories. | Frontend/backend/database; **M–L** | Decide validated assessment method |

### C. Essential security improvements

| Recommendation | Why / affected modules | Work and complexity | Dependencies |
|---|---|---|---|
| Implement authentication, password handling, and server-side authorization before multi-user deployment | Current API has no identity or access checks despite user/token tables in SQL. | Frontend/backend/database; **L** | Session/JWT design, secret management |
| Add rate limits and request-size/time controls | Unauthenticated matching launches a Python process and reads CSVs per request. | Backend/infrastructure; **M** | Hosting model and expected traffic |
| Establish production CORS and secret-handling rules | Local origins and local .env conventions are not a production security policy. | Backend/infrastructure; **M** | Deployment environment |

### D. Backend and database improvements

| Recommendation | Why / affected modules | Work and complexity | Dependencies |
|---|---|---|---|
| Connect the API to repositories and versioned migrations, or formally defer the SQL schema | Current schema and API are disconnected, and the schema count/seed documentation conflicts with source. | Backend/database; **L** | Decide persistence architecture |
| Cache matcher data or move matching into a persistent service | Each call starts Python and reloads data. | Backend/Python; **M–L** | Performance baseline and error contract |
| Make dataset imports update-safe and transactional | INSERT IGNORE does not refresh changed rows; table commits can leave partial data. | Python/database; **M** | Dataset-version strategy |
| Add health/readiness endpoints and structured operational logs | No standard signal indicates whether Java, Python, or data files are ready. | Backend/operations; **M** | Deployment target |

### E. Professional user experience improvements

| Recommendation | Why / affected modules | Work and complexity | Dependencies |
|---|---|---|---|
| Add URL-based routing and persisted profile state | Current conditional sections are not bookmarkable and browser Back does not navigate sections. | Frontend; **M** | Choose router and persistence model |
| Improve score explanations and caveats | Show base skill score, interest bonus, assessed-skill coverage, and that a score is not an employment probability. | Frontend; **S–M** | API response already contains score components |
| Add an accessible browser-level UI review | Current ARIA support exists, but contrast, keyboard flows, mobile navigation, and screen-reader behavior are unverified. | Frontend; **M** | Browser/device test plan |

### F. Advanced features

| Recommendation | Why / affected modules | Work and complexity | Dependencies |
|---|---|---|---|
| Add career-transition paths and what-if comparisons | Related schema concepts exist but no UI/API flow uses them. | Frontend/backend/database; **L** | Persistence and clear transition data |
| Add optional AI career guidance with safety and privacy controls | README describes this as a later phase; no implementation exists. | Frontend/backend/infrastructure; **XL** | Provider choice, consent, secrets, evaluation |
| Add evidence-backed skills and progress history | Schema proposes evidence and confidence but no upload, verification, or scoring logic exists. | Frontend/backend/database; **L–XL** | Verification workflow and privacy policy |

### G. Testing and deployment improvements

| Recommendation | Why / affected modules | Work and complexity | Dependencies |
|---|---|---|---|
| Update UI tests for the expanded workspace and theme/rating states | Current tests cover the quick profile only; they do not cover dashboard, roadmap, saved roles, interview, or settings. | Frontend; **M** | Stable UI behavior |
| Add full-stack and browser E2E tests | Existing test files do not establish a deployed browser-to-live-service flow. | Frontend/backend; **L** | Repeatable test services |
| Add CI, formatting/linting, dependency scanning, and release checks | No automation or dependency audit config was found. | Infrastructure; **M** | Repository/hosting decision |
| Create reproducible deployment and database backup instructions | Current startup is Windows-local and the Maven path is hard-coded. | Operations/backend; **M** | Target hosting platform |

## 12. Final project status

### Executive summary

This is a local prototype with a real Python career-matching engine, a Spring REST wrapper, a React profile/results flow, and several recently added frontend workspace sections. The project has substantial O*NET/ESCO processing code and a broad MySQL schema, but the database, authentication, and most schema-described product features are not connected to the live API. The browser-to-live-backend path and current test suite remain unverified in this audit.

### Exact features currently implemented in source

- Offline cleaning, normalization, inspection, and EDA scripts for local O*NET and ESCO data.
- O*NET career-skill map and metadata generation scripts.
- Python weighted career ranking, RIASEC bonus, and skill-gap logic.
- Java endpoints for skills and career matches, calling the Python matcher.
- Three-step quick profile with six ratings, interest choices, and result cards.
- Local skill-catalog fallback when the API is unavailable.
- Workspace sections for overview, career match, roadmap, interview prompts, saved careers, and settings.
- Local persistence for theme, saved roles, and roadmap completion.
- MySQL schema and CSV loader code, not integrated into application API persistence.
- Test source files in Python, Java, and frontend.

### Five most important technical weaknesses

1. **No authentication or authorization:** the current API is unauthenticated; the user/token schema is not implemented in Java.
2. **No application persistence:** profile and match results are transient; saved roles and roadmap progress are limited to one browser.
3. **Database/API split:** the wide MySQL schema and loader are not the persistence layer for the running API.
4. **Unverified end-to-end operation:** reports record prior component-level results, but the live browser → API → Python path is not verified here.
5. **Feature gaps behind the workspace UI:** interview practice is static, roadmap resources are not integrated, and data/search/compare capabilities are absent.

### Ten highest-priority improvements

1. Implement authentication and server-side authorization before any shared or public deployment.
2. Choose and implement a persistence strategy for profiles, matches, saved careers, and roadmap state.
3. Add an automated full-stack smoke test for the live browser/API/matcher flow.
4. Correct and version the SQL schema; reconcile its table count, seed instructions, and current API design.
5. Make matching data loading efficient for repeated requests.
6. Improve import transaction and update behavior for new dataset versions.
7. Add useful score explanations and clear limits to match-score interpretation.
8. Make interview drafts and roadmap interactions behave predictably across navigation and refresh.
9. Add accessible responsive UI review and complete workspace tests.
10. Add deployment, health-check, logging, backup, CI, and dependency-audit practices.

### Dependency-aware roadmap

1. **Stabilize the prototype:** agree on user flows and persistence boundaries; reconcile the SQL design and docs; add browser-to-backend smoke tests.
2. **Build the application foundation:** implement migrations/repositories and authentication, then persist profiles and match sessions. Add access controls before exposing user-specific data.
3. **Complete core guidance workflows:** persist saved careers and roadmaps; add resource-backed steps, score explanations, career search/compare, and a validated RIASEC assessment.
4. **Add practice and advanced guidance:** improve interview practice and optionally add AI only after privacy, consent, provider, and evaluation requirements are defined.
5. **Prepare for deployment:** add CI, health/readiness checks, production configuration, logs/metrics, backups, accessibility review, and dependency scanning.

### Current stack

- React 19, Vite 7, JavaScript, CSS.
- Java 24 target; Spring Boot 3.5.5; Maven.
- Python with pandas and NumPy; pytest.
- MySQL 8 schema and mysql-connector-python loader dependency.
- Local O*NET 31.0 and ESCO v1.2.1 data.

### Files likely to change for future work

- Frontend: frontend/src/App.jsx, frontend/src/styles.css, frontend/src/App.test.jsx, frontend/src/main.jsx, frontend/package.json, frontend/vite.config.js.
- API: backend/src/main/java/edu/careerguidance/api/, backend/src/main/java/edu/careerguidance/service/, backend/src/main/resources/application.yml, backend/pom.xml.
- Matching/data: data_processing/matching_engine.py, backend/matcher_cli.py, backend/load_onet_data.py, data_processing/build_career_skill_map.py.
- Persistence/security: backend/schema.sql and future migration/repository/security modules, which do not currently exist.
- Quality/docs/deployment: tests/, README.md, reports/, plus new CI and deployment configuration.

### Information not verified

- Current test and production-build results; no checks were run during this audit.
- Live Spring server, browser-to-backend connectivity, and current MySQL service/database state.
- Whether .env values are valid or whether the file is tracked outside the visible project configuration. Values were not disclosed.
- Whether the schema has been applied to any database.
- Current dependency vulnerability status.
- Accessibility behavior in a real browser or assistive technology.
- Target production hosting platform, monitoring, backups, and deployment process.

**No tests, builds, services, or dependency installs were run while creating this report.**
