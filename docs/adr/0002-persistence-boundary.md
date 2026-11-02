# ADR 0002: Persist only signed-in account data

- Status: Accepted
- Context: The application must preserve the existing guest experience while adding opt-in account history and progress.
- Decision: Keep guest profile and preferences in browser storage. When authentication is enabled, store profile snapshots, sessions/results, saved careers, roadmap state, and refresh-token hashes in MySQL through Flyway migrations. Scope reads and writes by the authenticated principal.
- Consequences: Guests can use matching without MySQL. Account mode requires a running database and migration startup. Local data is imported only after explicit user action.
