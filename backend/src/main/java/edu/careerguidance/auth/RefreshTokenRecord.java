package edu.careerguidance.auth;

import java.time.Instant;

public record RefreshTokenRecord(long id, long userId, String tokenHash, Instant expiresAt, Instant revokedAt) { }
