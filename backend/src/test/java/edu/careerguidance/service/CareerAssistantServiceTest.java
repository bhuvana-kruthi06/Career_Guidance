package edu.careerguidance.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CareerAssistantServiceTest {
    private CareerAIProvider provider;
    private CareerAssistantService assistant;

    @BeforeEach
    void setUp() {
        provider = mock(CareerAIProvider.class);
        ResourceCatalogService resources = mock(ResourceCatalogService.class);
        when(resources.all()).thenReturn(List.of());
        assistant = new CareerAssistantService(provider, resources, new ObjectMapper(), ".");
    }

    @Test
    void comparesJobDescriptionSkillsLocallyAndReturnsLearningActions() {
        Map<String, Object> profile = Map.of("skills", Map.of("Java", 4));
        Map<String, Object> target = Map.of("title", "Backend Developer");
        Map<String, Object> gaps = Map.of("skills", List.of(
                Map.of("name", "Java", "importance", 3.5), Map.of("name", "SQL", "importance", 4.0)));

        Map<String, Object> result = assistant.analyzeJobDescription(
                "Backend developer role. Required skills include Java and SQL. Build reliable APIs.", profile, target, Map.of(), gaps);

        assertEquals(List.of("Java"), result.get("matchedSkills"));
        assertEquals(List.of("SQL"), result.get("missingSkills"));
        assertEquals(List.of("SQL"), result.get("missingKeywords"));
        assertEquals(1, ((List<?>) result.get("recommendedLearningTasks")).size());
        verifyNoInteractions(provider);
    }

    @Test
    void interviewFeedbackUsesClearLocalFallbackWhenProviderIsUnavailable() {
        when(provider.complete(anyString(), anyString())).thenReturn(Optional.empty());

        Map<String, Object> result = assistant.evaluateAnswer("Describe a project", "I planned and built an API. The result improved response time.", "Backend Developer", "Intermediate");

        assertTrue(((String) result.get("feedback")).contains("cannot verify factual or technical correctness"));
        assertFalse(result.containsKey("aiFeedback"));
        assertTrue(((Number) result.get("score")).intValue() >= 0);
        verify(provider).complete(anyString(), anyString());
    }
}
