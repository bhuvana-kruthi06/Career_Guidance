package edu.careerguidance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Optional server-side OpenAI Chat Completions adapter. Empty credentials keep the app fully offline. */
@Component
public class OpenAiCareerProvider implements CareerAIProvider {
    private final String apiKey;
    private final String model;
    private final RestClient client;
    private final ObjectMapper json;

    public OpenAiCareerProvider(ObjectMapper json,
            @Value("${career-guidance.ai.api-key:}") String apiKey,
            @Value("${career-guidance.ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${career-guidance.ai.model:gpt-4o-mini}") String model) {
        this.apiKey = apiKey == null ? "" : apiKey.trim(); this.model = model; this.json = json;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5)); factory.setReadTimeout(Duration.ofSeconds(18));
        this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(factory).build();
    }
    @Override public boolean configured() { return !apiKey.isBlank(); }
    @Override public String providerName() { return "OpenAI"; }
    @Override public Optional<String> complete(String context, String prompt) {
        if (!configured()) return Optional.empty();
        try {
            Map<String, Object> request = Map.of("model", model, "max_completion_tokens", 500,
                    "messages", List.of(Map.of("role", "developer", "content", "You are a practical career coach. Use only the supplied context. Never invent experience, credentials, or facts. Give concise, actionable guidance."),
                            Map.of("role", "user", "content", "Career context (may be incomplete):\n" + context + "\n\nUser request:\n" + prompt)));
            String raw = client.post().uri("/chat/completions").contentType(MediaType.APPLICATION_JSON)
                    .headers(headers -> headers.setBearerAuth(apiKey)).body(request).retrieve().body(String.class);
            JsonNode root = json.readTree(raw); JsonNode content = root.path("choices").path(0).path("message").path("content");
            String text = content.isTextual() ? content.asText().trim() : "";
            return text.isBlank() ? Optional.empty() : Optional.of(text.length() > 5000 ? text.substring(0, 5000) : text);
        } catch (Exception ignored) { return Optional.empty(); }
    }
}
