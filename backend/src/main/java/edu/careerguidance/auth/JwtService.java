package edu.careerguidance.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class JwtService {
    private static final String ISSUER = "pathfinder";
    private static final String ALGORITHM = "HmacSHA256";
    private final ObjectMapper mapper;
    private final byte[] secret;
    private final long lifetimeSeconds;

    public JwtService(ObjectMapper mapper,
            @Value("${career-guidance.auth.jwt-secret:}") String secret,
            @Value("${career-guidance.auth.access-token-seconds:900}") long lifetimeSeconds) {
        this.mapper = mapper;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes when authentication is enabled.");
        if (lifetimeSeconds < 60 || lifetimeSeconds > 3600) throw new IllegalStateException("Access token lifetime must be between 60 and 3600 seconds.");
        this.lifetimeSeconds = lifetimeSeconds;
    }

    public String createAccessToken(UserAccount user) {
        Instant now = Instant.now();
        try {
            String header = encode(mapper.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            String payload = encode(mapper.writeValueAsBytes(Map.of(
                    "iss", ISSUER, "sub", Long.toString(user.id()), "email", user.email(), "role", user.role(),
                    "iat", now.getEpochSecond(), "exp", now.plusSeconds(lifetimeSeconds).getEpochSecond())));
            String signed = header + "." + payload;
            return signed + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(signed));
        } catch (Exception ex) { throw new IllegalStateException("Could not create an access token.", ex); }
    }

    public CurrentUser parseAccessToken(String token) {
        try {
            String[] parts = token.split("\\.", -1);
            if (parts.length != 3) throw new IllegalArgumentException("Invalid access token.");
            JsonNode header = mapper.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!"HS256".equals(header.path("alg").asText()) || !"JWT".equals(header.path("typ").asText())) {
                throw new IllegalArgumentException("Invalid access token.");
            }
            byte[] provided = Base64.getUrlDecoder().decode(parts[2]);
            if (!MessageDigest.isEqual(sign(parts[0] + "." + parts[1]), provided)) throw new IllegalArgumentException("Invalid access token.");
            JsonNode claims = mapper.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!ISSUER.equals(claims.path("iss").asText()) || claims.path("exp").asLong(0) <= Instant.now().getEpochSecond()) {
                throw new IllegalArgumentException("Invalid access token.");
            }
            long id = Long.parseLong(claims.path("sub").asText());
            String email = claims.path("email").asText();
            String role = claims.path("role").asText();
            if (id <= 0 || email.isBlank() || !("USER".equals(role) || "ADMIN".equals(role))) throw new IllegalArgumentException("Invalid access token.");
            return new CurrentUser(id, email, role);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Invalid access token.");
        }
    }

    private byte[] sign(String input) throws Exception {
        Mac mac = Mac.getInstance(ALGORITHM);
        mac.init(new SecretKeySpec(secret, ALGORITHM));
        return mac.doFinal(input.getBytes(StandardCharsets.US_ASCII));
    }
    private static String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public long lifetimeSeconds() { return lifetimeSeconds; }
}
