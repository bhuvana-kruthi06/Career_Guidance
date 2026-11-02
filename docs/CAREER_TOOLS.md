# Interview, job matching, and application tools

These endpoints require account mode and a valid bearer access token. Requests that change data also use the existing CSRF header/cookie flow. All records are scoped to the authenticated account.

## Interview coach

- `GET /api/me/interviews` — recent saved sessions.
- `POST /api/me/interviews` — create a five-question session. JSON: `{ "role": "Frontend developer", "type": "Technical", "difficulty": "Intermediate" }`. `role` is optional and defaults to the saved target role.
- `POST /api/me/interviews/{sessionId}/answers` — submit the current question: `{ "questionId": "q1", "answer": "..." }`. The server returns correctness, structure, communication, improvement points, and a sample answer. AI feedback uses the configured server-side provider; a local rubric remains available when the provider is absent or fails.
- `POST /api/me/interviews/{sessionId}/complete` — finish a fully answered session and save its readiness score and improvement report.

Session records use the existing `career_tool_records` table with record type `INTERVIEW`.

## Job description matcher

- `GET /api/me/job-descriptions` — list saved analyses.
- `GET /api/me/job-descriptions/{analysisId}` — fetch one saved analysis.
- `POST /api/me/job-descriptions` — compare an 80–12,000 character description with saved profile skills, resume extraction, and target-role requirements. Optional JSON fields: `company`, `jobTitle`.
- `DELETE /api/me/job-descriptions/{analysisId}` — delete a saved analysis.
- `POST /api/me/job-descriptions/{analysisId}/roadmap` — add deduplicated job-specific learning tasks to the job-preparation phase of the saved roadmap. A target-role roadmap is generated if one does not exist.

Job description analysis is a deterministic local comparison. It does not send the pasted description or profile skills to an external provider. Job analyses use record type `JOB_DESCRIPTION` in `career_tool_records`.

## Application tracker

- `GET /api/me/applications?status=Applied&search=Acme` — list/filter account-owned applications.
- `GET /api/me/applications/summary` — counts by status, active applications, scheduled interviews, offers, and follow-ups due within the next seven days (including overdue).
- `POST /api/me/applications` — create an application.
- `PUT /api/me/applications/{id}` — edit fields or move its status.
- `DELETE /api/me/applications/{id}` — delete an application.

An application contains `company`, `role`, `jobLink`, `salary`, `location`, `resumeVersion`, `applicationDate`, `status`, `notes`, and `followUpDate`. Supported statuses: `Saved`, `Applied`, `Interview Scheduled`, `Offer`, and `Rejected`. The tracker uses the existing `job_applications` table.

## Local verification

Run the frontend with `npm run build` and `npm test -- --run` from `frontend/`. Run backend tests with `mvn -f backend/pom.xml test`. The full authenticated Docker journey needs account mode, a generated local matcher catalog, configured local secrets, and working Docker Desktop. Do not use `docker compose down -v`; it deletes the MySQL volume.
