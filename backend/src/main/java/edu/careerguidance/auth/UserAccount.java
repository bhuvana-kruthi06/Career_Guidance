package edu.careerguidance.auth;

public record UserAccount(long id, String email, String passwordHash, String role, boolean active,
                          String fullName, String status, java.time.Instant lastActiveAt) {
    public UserAccount(long id, String email, String passwordHash, String role, boolean active) {
        this(id,email,passwordHash,role,active,null,active?"ACTIVE":"SUSPENDED",null);
    }
}
