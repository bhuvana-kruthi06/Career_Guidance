# Admin console

The optional account system now includes separate `/admin/login` and `/admin/*` experiences. User and admin refresh sessions use separate HTTP-only cookies, so both roles can stay signed in in the same browser profile (for example, user pages in one tab and admin pages in another). Logging out in one area leaves the other session intact. Public registrations always create `USER` accounts. Administrator credentials are never accepted from signup; the first administrator is inserted from server-only environment variables after the V6 Flyway migration has run.

## Initial administrator

Set all three values in the local, ignored `.env` file before restarting the backend:

```dotenv
ADMIN_EMAIL=admin@example.test
ADMIN_PASSWORD=use-a-private-password-at-least-12-characters
ADMIN_NAME=Platform Administrator
```

Do not commit `.env`. The seed runs only when all values are present, creates an administrator only when the email does not already exist, and does not reset an existing admin password on restart. If the configured email already belongs to a normal account, startup stops instead of silently promoting it. Create a dedicated admin email.

After changing environment variables or source, from the project root run:

```powershell
mvn -B -f backend/pom.xml test
mvn -B -f backend/pom.xml -DskipTests package
docker compose up --build -d backend frontend
docker compose ps
```

Then visit `http://localhost:8081/admin/login`. On local HTTP, `AUTH_COOKIE_SECURE=false` is required; use secure cookies behind HTTPS in a deployed environment.

## Admin API

All `/api/admin/**` endpoints require a valid bearer access token whose current database role and account status still match. Admin mutations also require the CSRF token. Suspension and archive revoke refresh tokens and immediately invalidate existing access tokens. Archive is soft; personal records remain for audit/history. The last active administrator and the signed-in administrator cannot be removed or disabled by their own action.

- `GET /api/admin/overview`
- `GET /api/admin/users` with search, role, status, target-role, activity, date, sort, and pagination filters
- `GET /api/admin/users/{id}`
- `PUT /api/admin/users/{id}`; `PUT /api/admin/users/{id}/status`; `PUT /api/admin/users/{id}/role`; `DELETE /api/admin/users/{id}` (archives)
- `POST /api/admin/users/{id}/notes`; `GET /api/admin/audit-logs`
- `GET /api/admin/records?type=profiles|resumes|assessments|roadmaps|applications`
- `GET|POST /api/admin/content`; `GET|PUT|DELETE /api/admin/content/{id}`; `PATCH /api/admin/content/{id}/enabled`

The admin content registry stores validated JSON records for career roles, skills, assessment questions, roadmap templates, project ideas, and learning resources. These entries are an admin-managed extension registry; the current O*NET, project, assessment, and curated-resource runtime catalogs still use their existing local sources. Password reset/email delivery is not implemented; the UI shows that limitation rather than pretending a reset was sent.

## Database changes

`V6__admin_console.sql` adds `full_name`, `account_status`, and `last_active_at` to `users`, plus `admin_user_notes`, `admin_audit_logs`, and `admin_content_items`. Account status values are `ACTIVE`, `SUSPENDED`, or `ARCHIVED`. The existing uppercase `USER` and `ADMIN` roles are retained for compatibility with JWT authorities and Spring Security. Content archives are soft-deleted.

## Manual verification

1. Register with a name; confirm the account is `USER` and cannot open admin routes or APIs.
2. Sign in through `/admin/login` using the seeded administrator; confirm `/admin/dashboard` loads.
3. Try `/admin/dashboard` and `/api/admin/overview` with a normal user token; expect a 403 response and user-facing redirect.
4. Search, filter, sort, and paginate users; open a user and inspect their profile/progress. Confirm mentor chat body text is not shown.
5. Suspend and reactivate a non-admin test user; verify suspension invalidates existing sessions.
6. Change a user role only after the confirmation prompt; confirm the event appears in Admin Settings audit logs.
7. Add an internal note; verify it appears only on the admin user detail page.
8. Create, edit, enable/disable, and archive an admin content entry.
9. Visit admin record pages for profiles, resumes, assessments, roadmaps, and applications.
10. Verify desktop and mobile layouts, plus the existing user dashboard and career modules.
