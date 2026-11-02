package edu.careerguidance.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Duration;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class AuthController {
    private final AuthService auth;
    private final boolean secureCookie;
    private final int refreshDays;

    public AuthController(AuthService auth,
            @Value("${career-guidance.auth.cookie-secure:false}") boolean secureCookie,
            @Value("${career-guidance.auth.refresh-token-days:30}") int refreshDays) {
        this.auth = auth;
        this.secureCookie = secureCookie;
        this.refreshDays = refreshDays;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(@RequestAttribute("_csrf") CsrfToken token) { return Map.of("token", token.getToken()); }

    @PostMapping("/register")
    public ResponseEntity<TokenResponse> register(@Valid @RequestBody RegistrationCredentials credentials, HttpServletRequest request,
            @RequestHeader(name = "X-Auth-Scope", defaultValue = "USER") String rawScope) {
        String scope = normalizeScope(rawScope);
        if (!"USER".equals(scope)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Administrator accounts cannot be created through signup.");
        AuthSession session = auth.register(credentials.email(), credentials.password(), credentials.name());
        return response(session, HttpStatus.CREATED, request);
    }

    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginCredentials credentials, HttpServletRequest request,
            @RequestHeader(name = "X-Auth-Scope", defaultValue = "USER") String rawScope) {
        String scope = normalizeScope(rawScope);
        AuthSession session = auth.login(credentials.email(), credentials.password());
        if (!scope.equals(session.user().role())) {
            auth.logout(session.refreshToken());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This account belongs to a different sign-in area.");
        }
        return response(session, HttpStatus.OK, request);
    }

    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(
            @CookieValue(name = "user_refresh_token", required = false) String userRefreshToken,
            @CookieValue(name = "admin_refresh_token", required = false) String adminRefreshToken,
            HttpServletRequest request,
            @RequestHeader(name = "X-Auth-Scope", defaultValue = "USER") String rawScope) {
        String scope = normalizeScope(rawScope);
        String refreshToken = "ADMIN".equals(scope) ? adminRefreshToken : userRefreshToken;
        AuthSession session = auth.refresh(refreshToken);
        if (!scope.equals(session.user().role())) {
            auth.logout(session.refreshToken());
            throw new AuthService.InvalidRefreshTokenException();
        }
        return response(session, HttpStatus.OK, request);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = "user_refresh_token", required = false) String userRefreshToken,
            @CookieValue(name = "admin_refresh_token", required = false) String adminRefreshToken,
            @RequestHeader(name = "X-Auth-Scope", defaultValue = "USER") String rawScope) {
        String scope = normalizeScope(rawScope);
        String refreshToken = "ADMIN".equals(scope) ? adminRefreshToken : userRefreshToken;
        auth.logout(refreshToken);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, expiredCookie(cookieName(scope)).toString());
        // Expire pre-scope cookies from the previous single-session implementation.
        headers.add(HttpHeaders.SET_COOKIE, expiredCookie("refresh_token").toString());
        return ResponseEntity.noContent().headers(headers).build();
    }

    private ResponseEntity<TokenResponse> response(AuthSession session, HttpStatus status, HttpServletRequest request) {
        String role = session.user().role();
        ResponseCookie cookie = ResponseCookie.from(cookieName(role), session.refreshToken())
                .httpOnly(true).secure(secureCookie || request.isSecure()).sameSite("Strict")
                .path("/api/auth").maxAge(Duration.ofDays(refreshDays)).build();
        TokenResponse body = new TokenResponse(session.accessToken(), "Bearer", session.expiresIn(), session.user());
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.SET_COOKIE, cookie.toString());
        // Clear the former shared cookie without touching the other role's session.
        headers.add(HttpHeaders.SET_COOKIE, expiredCookie("refresh_token").toString());
        return ResponseEntity.status(status).headers(headers).body(body);
    }

    private ResponseCookie expiredCookie(String name) {
        return ResponseCookie.from(name, "").httpOnly(true).secure(secureCookie)
                .sameSite("Strict").path("/api/auth").maxAge(Duration.ZERO).build();
    }

    private static String normalizeScope(String rawScope) {
        if (rawScope != null && ("USER".equalsIgnoreCase(rawScope) || "ADMIN".equalsIgnoreCase(rawScope))) return rawScope.toUpperCase(java.util.Locale.ROOT);
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Sign-in scope must be USER or ADMIN.");
    }

    private static String cookieName(String scope) { return "ADMIN".equals(scope) ? "admin_refresh_token" : "user_refresh_token"; }

    public record RegistrationCredentials(@NotBlank @Email @Size(max = 254) String email,
                                          @NotBlank @Size(min = 12, max = 128) String password,
                                          @NotBlank @Size(max = 80) String name) { }
    public record LoginCredentials(@NotBlank @Email @Size(max = 254) String email,
                                   @NotBlank @Size(max = 128) String password) { }
    public record TokenResponse(String accessToken, String tokenType, long expiresIn, AuthUserView user) { }
}
