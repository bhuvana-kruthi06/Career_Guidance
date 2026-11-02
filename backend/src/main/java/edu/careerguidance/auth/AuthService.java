package edu.careerguidance.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class AuthService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final AuthRepository repository;
    private final PasswordEncoder passwords;
    private final JwtService jwt;
    private final int refreshDays;

    public AuthService(AuthRepository repository, PasswordEncoder passwords, JwtService jwt,
            @Value("${career-guidance.auth.refresh-token-days:30}") int refreshDays) {
        this.repository = repository;
        this.passwords = passwords;
        this.jwt = jwt;
        if (refreshDays < 1 || refreshDays > 90) throw new IllegalStateException("Refresh token lifetime must be between 1 and 90 days.");
        this.refreshDays = refreshDays;
    }

    @Transactional
    public AuthSession register(String rawEmail, String password, String rawName) {
        String email = normalizeEmail(rawEmail);
        if (repository.findUserByEmail(email).isPresent()) throw new AccountAlreadyExistsException();
        UserAccount user;
        String name = rawName == null ? "" : rawName.trim();
        try { user = repository.createUser(email, passwords.encode(password), name.isBlank() ? null : name); }
        catch (DuplicateKeyException ex) { throw new AccountAlreadyExistsException(); }
        return createSession(user);
    }

    @Transactional
    public AuthSession login(String rawEmail, String password) {
        String email = normalizeEmail(rawEmail);
        UserAccount user = repository.findUserByEmail(email).orElse(null);
        if (user == null || !user.active() || !"ACTIVE".equals(user.status()) || !passwords.matches(password, user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        return createSession(user);
    }

    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public AuthSession refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) throw new InvalidRefreshTokenException();
        RefreshTokenRecord token = repository.findRefreshToken(hash(refreshToken)).orElseThrow(InvalidRefreshTokenException::new);
        Instant now = Instant.now();
        if (token.revokedAt() != null) {
            repository.revokeAllRefreshTokens(token.userId());
            throw new InvalidRefreshTokenException();
        }
        if (!token.expiresAt().isAfter(now)) {
            repository.revokeToken(token.id());
            throw new InvalidRefreshTokenException();
        }
        UserAccount user = repository.findUserById(token.userId()).filter(UserAccount::active)
                .orElseThrow(InvalidRefreshTokenException::new);
        if (!"ACTIVE".equals(user.status())) throw new InvalidRefreshTokenException();
        String nextToken = randomToken();
        String nextHash = hash(nextToken);
        if (!repository.revokeRefreshToken(token.id(), now, nextHash)) {
            repository.revokeAllRefreshTokens(token.userId());
            throw new InvalidRefreshTokenException();
        }
        Instant expiresAt = now.plus(refreshDays, ChronoUnit.DAYS);
        repository.storeRefreshToken(user.id(), nextHash, expiresAt);
        return new AuthSession(jwt.createAccessToken(user), jwt.lifetimeSeconds(), AuthUserView.from(user), nextToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) return;
        repository.findRefreshToken(hash(refreshToken)).ifPresent(token -> repository.revokeToken(token.id()));
    }

    public AuthUserView currentUser(CurrentUser principal) {
        return repository.findUserById(principal.id()).filter(UserAccount::active)
                .map(AuthUserView::from).orElseThrow(InvalidCredentialsException::new);
    }

    private AuthSession createSession(UserAccount user) {
        repository.touchLastActive(user.id());
        String refreshToken = randomToken();
        repository.storeRefreshToken(user.id(), hash(refreshToken), Instant.now().plus(refreshDays, ChronoUnit.DAYS));
        return new AuthSession(jwt.createAccessToken(user), jwt.lifetimeSeconds(), AuthUserView.from(user), refreshToken);
    }

    private static String normalizeEmail(String email) { return email.trim().toLowerCase(java.util.Locale.ROOT); }
    private static String randomToken() {
        byte[] bytes = new byte[48];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    private static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception ex) { throw new IllegalStateException("Could not hash refresh token.", ex); }
    }

    public static final class InvalidCredentialsException extends RuntimeException {
        public InvalidCredentialsException() { super("Email or password is incorrect."); }
    }
    public static final class InvalidRefreshTokenException extends RuntimeException {
        public InvalidRefreshTokenException() { super("Refresh token is invalid or expired."); }
    }
    public static final class AccountAlreadyExistsException extends RuntimeException {
        public AccountAlreadyExistsException() { super("An account already exists for this email."); }
    }
}
