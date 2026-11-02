package edu.careerguidance.auth;

public record AuthUserView(long id, String email, String role, String name) {
    public static AuthUserView from(UserAccount user) { return new AuthUserView(user.id(), user.email(), user.role(), user.fullName()); }
}
