package edu.careerguidance.api;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.careerguidance.service.MatcherBridge;
import edu.careerguidance.service.MatcherUnavailableException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
        "career-guidance.rate-limit.capacity=3",
        "career-guidance.matcher.max-concurrent=1",
        "career-guidance.request.max-body-bytes=1024"
})
@AutoConfigureMockMvc
class ApiHardeningTest {
    private static final String MATCH_REQUEST = "{\"studentSkills\":{\"Critical Thinking\":4,\"Writing\":3,\"Programming\":4},\"studentInterests\":[\"I\"],\"topN\":10,\"assessedOnly\":true}";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean MatcherBridge matcher;

    @BeforeEach
    void prepareMatcher() throws Exception {
        when(matcher.match(anyMap())).thenReturn(JsonNodeFactory.instance.arrayNode());
        when(matcher.skills()).thenReturn(mapper.readTree("[{\"id\":1}]") );
    }

    @Test
    void rateLimitsMatchesPerClientAndReturnsRetryHint() throws Exception {
        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/api/matches").with(request -> { request.setRemoteAddr("192.0.2.11"); return request; })
                            .contentType(MediaType.APPLICATION_JSON).content(MATCH_REQUEST))
                    .andExpect(status().isOk());
        }
        mvc.perform(post("/api/matches").with(request -> { request.setRemoteAddr("192.0.2.11"); return request; })
                        .contentType(MediaType.APPLICATION_JSON).content(MATCH_REQUEST))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().exists("X-Request-ID"))
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("too many match requests")));
    }

    @Test
    void rejectsOversizedBodiesBeforeControllerParsing() throws Exception {
        String oversized = "{" + " ".repeat(1100) + "}";
        mvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON).content(oversized))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("too large")));
    }

    @Test
    void servesOpenApiDescriptionWithProjectMetadata() throws Exception {
        mvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("Pathfinder Career Guidance API"))
                .andExpect(jsonPath("$.paths['/api/matches']").exists());
    }

    @Test
    void capsConcurrentMatcherCallsAndReleasesTheSlot() throws Exception {
        CountDownLatch enteredMatcher = new CountDownLatch(1);
        CountDownLatch releaseMatcher = new CountDownLatch(1);
        when(matcher.match(anyMap())).thenAnswer(invocation -> {
            enteredMatcher.countDown();
            if (!releaseMatcher.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("test timeout");
            return JsonNodeFactory.instance.arrayNode();
        });

        CompletableFuture<MvcResult> first = CompletableFuture.supplyAsync(() -> {
            try {
                return mvc.perform(post("/api/matches").with(request -> { request.setRemoteAddr("192.0.2.22"); return request; })
                        .contentType(MediaType.APPLICATION_JSON).content(MATCH_REQUEST)).andReturn();
            } catch (Exception ex) { throw new RuntimeException(ex); }
        });
        if (!enteredMatcher.await(5, TimeUnit.SECONDS)) throw new AssertionError("First matcher call did not begin.");
        try {
            mvc.perform(post("/api/matches").with(request -> { request.setRemoteAddr("192.0.2.22"); return request; })
                            .contentType(MediaType.APPLICATION_JSON).content(MATCH_REQUEST))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(header().exists("Retry-After"))
                    .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString("handling too many requests")));
        } finally {
            releaseMatcher.countDown();
        }
        org.junit.jupiter.api.Assertions.assertEquals(200, first.get(6, TimeUnit.SECONDS).getResponse().getStatus());
        mvc.perform(post("/api/matches").with(request -> { request.setRemoteAddr("192.0.2.22"); return request; })
                        .contentType(MediaType.APPLICATION_JSON).content(MATCH_REQUEST))
                .andExpect(status().isOk());
    }

    @Test
    void exposesMatcherReadinessAndReportsBrokenMatcherAsDown() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("UP"));
        when(matcher.skills()).thenThrow(new MatcherUnavailableException("not exposed to response"));
        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void rejectsTopNAboveTheConfiguredMaximum() throws Exception {
        Map<String, Object> payload = Map.of("studentSkills", Map.of("One", 3, "Two", 3, "Three", 4),
                "studentInterests", java.util.List.of("I"), "topN", 26, "assessedOnly", true);
        mvc.perform(post("/api/matches").with(request -> { request.setRemoteAddr("192.0.2.33"); return request; })
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(payload)))
                .andExpect(status().isBadRequest());
    }
}
