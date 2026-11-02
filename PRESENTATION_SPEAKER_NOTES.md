# Presentation–2 Speaker Notes

**Suggested split:** Member 1 — B23CS024; Member 2 — B23CS023; Member 3 — B23CS032; Member 4 — B23CS054. Swap if your team prefers. Add the college name on slide 1 before presenting.

## Slide-by-slide speaking points

| Slide | Speaker | Short talk track |
|---|---|---|
| 1. Title | 1 | Introduce Pathfinder and the team. Say the platform connects a student’s skills and interests to occupational requirements and learning actions. Replace the college placeholder. |
| 2. Motivation | 1 | A useful recommendation needs profile evidence, role requirements, and a next step. The generated data currently includes 1,016 occupations and uses 68 selected skill dimensions. Scores are guidance, not hiring probabilities. |
| 3. Problem | 1 | Students need to know which roles fit, what is missing, and what to learn next. The project connects these questions in one workflow. |
| 4. Design gap | 1 | Separate tools can cover career information, assessment, learning, or chat. Our design connects profile, role matching, prioritized gaps, and next actions. This is our design rationale, not a formal literature-review result. |
| 5. Modules | 2 | Summarize profile/assessment, matching, growth planning, resume analysis, career tools, and account/admin support. AI is optional; matching uses deterministic scoring. |
| 6. Stack | 2 | React/Vite is the UI; Spring Boot is the API/security layer; Python/pandas handles the pipeline and matcher; MySQL/Flyway stores optional account data. Docker packages the local app. Codex is a development assistant, not runtime technology. |
| 7. Data | 1 | Show the real source-file counts and preprocessing. O*NET drives scores; ESCO is prepared as supplementary data but is not yet part of the matching formula. Do not imply all 19 ESCO files are used by the cleaner: five core ESCO files are currently selected there. |
| 8. Schema | 2 | Explain users and per-user profile/session/assessment/resume/roadmap/application records. Flyway V1–V7 are the runtime account schema; the standalone schema.sql is an older design. |
| 9. Architecture | 2 | Trace React → Spring → MatcherBridge → Python matcher → generated O*NET CSVs, then results back to the UI. MySQL is optional for signed-in persistence. LLM calls are separate from matching and only support mentor/interview guidance. |
| 10. Workflow | 3 | Profile and ratings enter the API; ratings map to the O*NET scale; weighted coverage and interest overlap rank roles; gaps are prioritized; the UI presents roles and learning actions. |
| 11. Screenshots | 3 | These are genuine screenshots supplied in earlier project sessions. The profile screenshot visibly shows a fetch error; the job-matcher screenshot is an empty input form. They are not successful result evidence. Explain that current running screenshots could not be captured because Docker was unavailable. |
| 12. Results | 3 | The chart and counts are from the processed dataset. Test counts come from dated Phase 9/10 reports and were not rerun for this deck. Docker startup and browser E2E were explicitly unverified; no recommendation accuracy is claimed. |
| 13. Limitations | 4 | Be direct about self-reported skills, heuristic resume scoring, optional AI, local data dependency, missing OCR, and no automatic fake-profile detector. Next, verify the full stack and evaluate on a documented set. |
| 14. Demo | 4 | Demo only after all containers are healthy. Start with profile, ratings/interests, match/gaps, and roadmap. If the backend is not healthy, show the data/matching workflow and explain the blocker rather than pretending the screenshot is a successful output. |
| 15. References | 4 | Point to O*NET, ESCO, and repository docs. Invite questions. |

## Likely questions and short answers

1. **What problem does the project solve?** It helps students connect their current skills and interests to career options, skill gaps, and possible learning actions.
2. **What is the main contribution?** An explainable workflow combining occupational data, a weighted skill match, prioritized gaps, and career-planning tools.
3. **Which dataset drives the recommendations?** O*NET 31.0 currently drives matching. ESCO v1.2.1 is cleaned as supplementary data but is not used in the scoring engine.
4. **How is the match score calculated?** Each skill’s coverage is weighted by its O*NET importance; the weighted average is converted to a percentage, with a small capped interest-overlap bonus.
5. **Does a 75% score mean a 75% chance of getting hired?** No. It measures similarity between self-reported skills/interests and the selected occupation data.
6. **Where is AI used?** An optional server-side OpenAI-compatible provider can assist mentor and interview feedback. Career matching and job-description comparison are deterministic; local fallback works when AI is not configured or fails.
7. **Why use both Java and Python?** Python already contains the data pipeline and scoring engine. Spring Boot provides the API, validation, security, and persistence boundary.
8. **Why MySQL and Flyway?** MySQL stores user-owned information; Flyway applies versioned SQL migrations. Guest use can keep data in the browser.
9. **What does the resume score mean?** It is a heuristic completeness and keyword signal, not an employer ATS result or guarantee.
10. **How was it tested?** Earlier phase reports record Python, backend, and frontend tests passing. They are dated results, not rerun for this presentation; Docker and browser E2E remain unverified.
11. **What are the limitations?** Self-rated skills, approximate skill-name mapping, local data setup, heuristic resume analysis, no OCR, and no measured recommendation accuracy.
12. **What is next?** Verify Docker and the authenticated end-to-end journey, create a documented evaluation set, then consider OCR or safer account-abuse review.

## Last-minute checklist

- Fill in the college name on slide 1; add team names if required by the department.
- The two named reference decks were not found in the accessible project/Downloads/Documents/attachment folders. The project’s Phase-I PPT was available locally.
- The profile screenshot displays **Failed to fetch**. Keep its caption and explain it honestly; do not describe it as a successful match.
- Docker was stopped/unavailable during preparation. If demonstrating, build the backend JAR, start Compose, wait for healthy status, and verify one complete match before the presentation.
- Do not display `.env`, passwords, JWT secrets, or API keys. Avoid `docker compose down -v` because it deletes the database volume.
- Do not claim match accuracy, hiring probability, automated fake-account detection, resume OCR, or successful live E2E.
