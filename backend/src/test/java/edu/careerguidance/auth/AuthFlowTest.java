package edu.careerguidance.auth;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.careerguidance.service.MatcherBridge;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockCookie;

@SpringBootTest(properties = {
        "career-guidance.auth.enabled=true",
        "career-guidance.auth.jwt-secret=auth-test-secret-that-is-at-least-thirty-two-bytes-long",
        "career-guidance.auth.cookie-secure=false",
        "spring.flyway.enabled=true",
        "spring.flyway.locations=classpath:db/migration-h2",
        "DATABASE_URL=jdbc:h2:mem:authflow;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "DATABASE_USERNAME=sa",
        "DATABASE_PASSWORD=",
        "career-guidance.rate-limit.capacity=100"
        ,"career-guidance.auth.login-rate-capacity=2"
        ,"career-guidance.auth.registration-rate-capacity=100"
})
@AutoConfigureMockMvc
class AuthFlowTest {
    private static final String TEST_SECRET = "auth-test-secret-that-is-at-least-thirty-two-bytes-long";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtService jwt;
    @MockitoBean MatcherBridge matcher;

    @org.junit.jupiter.api.BeforeEach
    void stubPublicMatcher() {
        when(matcher.skills()).thenReturn(JsonNodeFactory.instance.arrayNode().addObject().put("id", 1));
        when(matcher.match(org.mockito.ArgumentMatchers.anyMap())).thenReturn(JsonNodeFactory.instance.arrayNode());
    }

    @Test
    void registersBcryptCredentialSetsSafeRefreshCookieAndProtectsMe() throws Exception {
        MockHttpServletResponse response = register("student@example.com");
        org.junit.jupiter.api.Assertions.assertEquals(201, response.getStatus());
        var json = mapper.readTree(response.getContentAsString());
        String access = json.path("accessToken").asText();
        org.junit.jupiter.api.Assertions.assertFalse(access.isBlank());
        org.junit.jupiter.api.Assertions.assertEquals("USER", json.path("user").path("role").asText());
        String setCookie = response.getHeader("Set-Cookie");
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("HttpOnly"));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("SameSite=Strict"));
        org.junit.jupiter.api.Assertions.assertTrue(setCookie.contains("Path=/api/auth"));
        String passwordHash = jdbc.queryForObject("SELECT password_hash FROM users WHERE email=?", String.class, "student@example.com");
        org.junit.jupiter.api.Assertions.assertTrue(passwordHash.startsWith("$2a$"));
        org.junit.jupiter.api.Assertions.assertNotEquals("a-strong-password-123", passwordHash);
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + access))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("student@example.com"));
    }

    @Test
    void rejectsWrongPasswordsWithOneGenericLoginMessage() throws Exception {
        register("wrong-password@example.com");
        mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr("198.51.100.11"); return request; }).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("email", "wrong-password@example.com", "password", "incorrect-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Email or password is incorrect."));
        mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr("198.51.100.12"); return request; }).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("email", "unknown@example.com", "password", "incorrect-password"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Email or password is incorrect."));
    }

    @Test
    void rotatesRefreshTokensAndRevokesAllTokensWhenAnOldTokenIsReused() throws Exception {
        MockHttpServletResponse registered = register("rotation@example.com");
        String original = registered.getCookie("user_refresh_token").getValue();
        CsrfData csrf = csrfData();
        MockHttpServletResponse refreshed = mvc.perform(post("/api/auth/refresh").cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value()).cookie(new MockCookie("user_refresh_token", original)))
                .andExpect(status().isOk()).andReturn().getResponse();
        String rotated = refreshed.getCookie("user_refresh_token").getValue();
        org.junit.jupiter.api.Assertions.assertNotEquals(original, rotated);

        csrf = csrfData();
        mvc.perform(post("/api/auth/refresh").cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value()).cookie(new MockCookie("user_refresh_token", original)))
                .andExpect(status().isUnauthorized());
        csrf = csrfData();
        mvc.perform(post("/api/auth/refresh").cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value()).cookie(new MockCookie("user_refresh_token", rotated)))
                .andExpect(status().isUnauthorized());
        org.junit.jupiter.api.Assertions.assertEquals(0, jdbc.queryForObject(
                "SELECT COUNT(*) FROM refresh_tokens WHERE user_id=(SELECT id FROM users WHERE email=?) AND revoked_at IS NULL",
                Integer.class, "rotation@example.com"));
    }

    @Test
    void rejectsExpiredRefreshTokens() throws Exception {
        MockHttpServletResponse registered = register("expired-refresh@example.com");
        String refresh = registered.getCookie("user_refresh_token").getValue();
        jdbc.update("UPDATE refresh_tokens SET expires_at=TIMESTAMP '2000-01-01 00:00:00' WHERE user_id=(SELECT id FROM users WHERE email=?)",
                "expired-refresh@example.com");
        CsrfData csrf = csrfData();
        mvc.perform(post("/api/auth/refresh").cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.value())
                        .cookie(new MockCookie("user_refresh_token", refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void keepsAdminAndUserRefreshSessionsIndependentInTheSameBrowser() throws Exception {
        MockHttpServletResponse userSignup = register("parallel-user@example.com");
        String userCookie = userSignup.getCookie("user_refresh_token").getValue();
        register("parallel-admin@example.com");
        jdbc.update("UPDATE users SET role='ADMIN' WHERE email=?", "parallel-admin@example.com");

        MockHttpServletResponse adminLogin = mvc.perform(post("/api/auth/login")
                        .header("X-Auth-Scope", "ADMIN").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("email", "parallel-admin@example.com", "password", "a-strong-password-123"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn().getResponse();
        String adminCookie = adminLogin.getCookie("admin_refresh_token").getValue();

        CsrfData csrf = csrfData();
        MockHttpServletResponse userRefresh = mvc.perform(post("/api/auth/refresh").header("X-Auth-Scope", "USER")
                        .cookie(csrf.cookie(), new MockCookie("user_refresh_token", userCookie), new MockCookie("admin_refresh_token", adminCookie))
                        .header("X-XSRF-TOKEN", csrf.value()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("USER"))
                .andReturn().getResponse();
        userCookie = userRefresh.getCookie("user_refresh_token").getValue();

        csrf = csrfData();
        MockHttpServletResponse adminRefresh = mvc.perform(post("/api/auth/refresh").header("X-Auth-Scope", "ADMIN")
                        .cookie(csrf.cookie(), new MockCookie("user_refresh_token", userCookie), new MockCookie("admin_refresh_token", adminCookie))
                        .header("X-XSRF-TOKEN", csrf.value()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andReturn().getResponse();
        adminCookie = adminRefresh.getCookie("admin_refresh_token").getValue();

        csrf = csrfData();
        mvc.perform(post("/api/auth/logout").header("X-Auth-Scope", "USER")
                        .cookie(csrf.cookie(), new MockCookie("user_refresh_token", userCookie), new MockCookie("admin_refresh_token", adminCookie))
                        .header("X-XSRF-TOKEN", csrf.value()))
                .andExpect(status().isNoContent());
        csrf = csrfData();
        mvc.perform(post("/api/auth/refresh").header("X-Auth-Scope", "ADMIN")
                        .cookie(csrf.cookie(), new MockCookie("admin_refresh_token", adminCookie))
                        .header("X-XSRF-TOKEN", csrf.value()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("ADMIN"));
    }

    @Test
    void acceptsAndStoresAssessmentSubmissionsLargerThanTheGenericJsonLimit() throws Exception {
        MockHttpServletResponse registered = register("large-assessment@example.com");
        String accessToken = mapper.readTree(registered.getContentAsString()).path("accessToken").asText();
        String longReflection = "career assessment response ".repeat(1_000);
        CsrfData csrf = csrfData();
        mvc.perform(post("/api/me/assessments").header("Authorization", "Bearer " + accessToken)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.value()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("assessment", Map.of("longReflection", longReflection), "recommendations", java.util.List.of()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.assessment.longReflection").value(longReflection));
    }

    @Test
    void roadmapWithoutATargetRoleReportsThatPrerequisiteExplicitly() throws Exception {
        MockHttpServletResponse registered = register("roadmap-prerequisite@example.com");
        String accessToken = mapper.readTree(registered.getContentAsString()).path("accessToken").asText();
        mvc.perform(get("/api/me/roadmap").header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.generated").value(false))
                .andExpect(jsonPath("$.hasTargetRole").value(false)).andExpect(jsonPath("$.phases").isEmpty());
    }

    @Test
    void enforces401403AndKeepsGuestEndpointsOpen() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized());
        MockHttpServletResponse registered = register("role-check@example.com");
        String userAccess = mapper.readTree(registered.getContentAsString()).path("accessToken").asText();
        mvc.perform(get("/api/admin/status").header("Authorization", "Bearer " + userAccess)).andExpect(status().isForbidden());
        mvc.perform(get("/api/skills")).andExpect(status().isOk());
        mvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentSkills\":{\"One\":3,\"Two\":4,\"Three\":3},\"studentInterests\":[\"I\"],\"assessedOnly\":true}"))
                .andExpect(status().isOk());

        Long id = jdbc.queryForObject("SELECT id FROM users WHERE email=?", Long.class, "role-check@example.com");
        jdbc.update("UPDATE users SET role='ADMIN' WHERE id=?", id);
        UserAccount admin = new UserAccount(id, "role-check@example.com", "unused", "ADMIN", true);
        mvc.perform(get("/api/admin/status").header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ADMIN_ACCESS_GRANTED"));
        mvc.perform(get("/api/admin/users?page=0&size=10").header("Authorization", "Bearer " + jwt.createAccessToken(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items[0].role").value("ADMIN"));
    }

    @Test
    void rejectsExpiredJwtAccessTokens() throws Exception {
        mvc.perform(get("/api/me").header("Authorization", "Bearer " + expiredToken()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void enforcesPasswordLengthAtRegistration() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("email", "short@example.com", "password", "short", "name", "Short User"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("12 characters")));
    }

    @Test
    void rateLimitsLoginAttemptsByClient() throws Exception {
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr("192.0.2.88"); return request; })
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsBytes(Map.of("email", "unknown@example.com", "password", "incorrect-password"))))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr("192.0.2.88"); return request; })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("email", "unknown@example.com", "password", "incorrect-password"))))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After"));
    }

    @Test
    void scopesProfilesAndMatchHistoryToTheVerifiedOwnerAndImportsOnlyOnce() throws Exception {
        String tokenA = mapper.readTree(register("owner-a@example.com").getContentAsString()).path("accessToken").asText();
        String tokenB = mapper.readTree(register("owner-b@example.com").getContentAsString()).path("accessToken").asText();
        CsrfData csrf = csrfData();
        mvc.perform(put("/api/me/profile").header("Authorization", "Bearer " + tokenA)
                        .cookie(csrf.cookie()).header("X-XSRF-TOKEN", csrf.value()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":{\"name\":\"Mira\",\"stage\":\"Student\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Mira"));
        mvc.perform(get("/api/me/profile").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").doesNotExist());

        mvc.perform(post("/api/matches").header("Authorization", "Bearer " + tokenA).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"studentSkills\":{\"One\":3,\"Two\":4,\"Three\":3},\"studentInterests\":[\"I\"],\"assessedOnly\":true}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/sessions").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].skills.One").value(3));
        Long sessionId = jdbc.queryForObject("SELECT id FROM match_sessions WHERE user_id=(SELECT id FROM users WHERE email=?)", Long.class, "owner-a@example.com");
        mvc.perform(get("/api/me/sessions/" + sessionId).header("Authorization", "Bearer " + tokenB)).andExpect(status().isNotFound());

        csrf = csrfData();
        mvc.perform(post("/api/me/saved-careers").header("Authorization", "Bearer " + tokenA).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"onetSocCode\":\"15-1252.00\",\"career\":{\"onet_soc_code\":\"15-1252.00\",\"occupation_title\":\"Software Developers\",\"match_score\":80,\"description\":\"Build software.\"}}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/saved-careers").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].onet_soc_code").value("15-1252.00"));
        mvc.perform(get("/api/me/saved-careers").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        csrf = csrfData();
        mvc.perform(put("/api/me/roadmap").header("Authorization", "Bearer " + tokenA).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"key\":\"skill-one\",\"title\":\"Build a skill\",\"complete\":true,\"completedAt\":\"2026-10-27T10:00:00Z\"}]}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/me/roadmap").header("Authorization", "Bearer " + tokenA))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].complete").value(true)).andExpect(jsonPath("$[0].completedAt").isNotEmpty());
        mvc.perform(get("/api/me/roadmap").header("Authorization", "Bearer " + tokenB))
                .andExpect(status().isOk()).andExpect(jsonPath("$").isEmpty());

        csrf = csrfData();
        mvc.perform(post("/api/me/import-local").header("Authorization", "Bearer " + tokenA).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value()).contentType(MediaType.APPLICATION_JSON).content("{\"profile\":{\"name\":\"Mira\"},\"savedCareers\":[],\"roadmap\":[]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(true));
        csrf = csrfData();
        mvc.perform(post("/api/me/import-local").header("Authorization", "Bearer " + tokenA).cookie(csrf.cookie())
                        .header("X-XSRF-TOKEN", csrf.value()).contentType(MediaType.APPLICATION_JSON).content("{\"profile\":{\"name\":\"Other\"}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.imported").value(false));
    }


    @Test
    void shortLoginPasswordStillGetsGenericUnauthorizedResponse() throws Exception {
        mvc.perform(post("/api/auth/login").with(request -> { request.setRemoteAddr("198.51.100.13"); return request; }).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("email", "unknown@example.com", "password", "short"))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.error").value("Email or password is incorrect."));
    }

    private MockHttpServletResponse register(String email) throws Exception {
        return mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsBytes(Map.of("email", email, "password", "a-strong-password-123", "name", "Test User"))))
                .andExpect(status().isCreated()).andExpect(header().exists("Set-Cookie"))
                .andReturn().getResponse();
    }

    private CsrfData csrfData() throws Exception {
        MockHttpServletResponse response = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn().getResponse();
        return new CsrfData(new MockCookie("XSRF-TOKEN", response.getCookie("XSRF-TOKEN").getValue()),
                mapper.readTree(response.getContentAsString()).path("token").asText());
    }

    private record CsrfData(MockCookie cookie, String value) { }

    private String expiredToken() throws Exception {
        String header = Base64.getUrlEncoder().withoutPadding().encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"iss\":\"pathfinder\",\"sub\":\"1\",\"email\":\"expired@example.com\",\"role\":\"USER\",\"iat\":1,\"exp\":2}".getBytes(StandardCharsets.UTF_8));
        String signingInput = header + "." + payload;
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(TEST_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return signingInput + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(signingInput.getBytes(StandardCharsets.US_ASCII)));
    }
}
