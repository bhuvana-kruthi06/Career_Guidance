package edu.careerguidance.auth;

public record AuthSession(String accessToken, long expiresIn, AuthUserView user, String refreshToken) { }
