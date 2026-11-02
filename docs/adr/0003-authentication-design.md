# ADR 0003: Short-lived access tokens and rotating refresh cookies

- Status: Accepted
- Context: Accounts need API authentication without storing long-lived bearer credentials in browser storage.
- Decision: Hash passwords with BCrypt, hold short-lived JWT access tokens in frontend memory, store only hashed rotating refresh tokens in MySQL, and send refresh tokens in HttpOnly SameSite=Strict cookies. Require CSRF tokens for cookie-authenticated mutations.
- Consequences: Clients refresh access silently and must handle session expiry. Deployments require a private JWT secret, HTTPS, secure cookies, and exact CORS origins. Auth remains disabled by default for guest development.
