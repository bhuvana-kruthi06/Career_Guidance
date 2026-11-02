package edu.careerguidance.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import edu.careerguidance.service.CareerAssistantService;
import edu.careerguidance.service.CareerCatalogService;
import edu.careerguidance.service.ResourceCatalogService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CareerToolsControllerTest {
    @Test
    void jobDescriptionAnalysisCopiesImmutableServiceResultBeforeAddingMetadata() {
        CareerToolsRepository tools = mock(CareerToolsRepository.class);
        CareerIntelligenceRepository intelligence = mock(CareerIntelligenceRepository.class);
        CareerIntelligenceController careerIntelligence = mock(CareerIntelligenceController.class);
        CareerAssistantService assistant = mock(CareerAssistantService.class);
        CareerToolsController controller = new CareerToolsController(tools, intelligence, careerIntelligence,
                mock(AccountDataRepository.class), mock(CareerCatalogService.class), mock(ResourceCatalogService.class), assistant);
        String description = "Junior analyst will use SQL, Excel, and dashboards to analyze data and communicate findings to teams.";
        when(intelligence.profile(7L)).thenReturn(Map.of());
        when(intelligence.targetRole(7L)).thenReturn(Optional.empty());
        when(intelligence.latestResume(7L)).thenReturn(Optional.empty());
        when(careerIntelligence.skillGapReport(7L)).thenReturn(Map.of("skills", List.of()));
        when(assistant.analyzeJobDescription(eq(description), anyMap(), anyMap(), anyMap(), anyMap()))
                .thenReturn(Map.of("matchPercent", 74, "matchedSkills", List.of("SQL")));

        Map<String, Object> result = controller.analyzeJobDescription(new CurrentUser(7L, "user@example.test", "USER"),
                new CareerToolsController.JobDescriptionRequest(description, "Sample Analytics", "Junior Data Analyst"));

        assertEquals(Integer.valueOf(74), result.get("matchPercent"));
        assertEquals("Sample Analytics", result.get("company"));
        assertEquals("Junior Data Analyst", result.get("jobTitle"));
        assertEquals(description, result.get("description"));
        assertNotNull(result.get("analysisId"));
        @SuppressWarnings("unchecked") ArgumentCaptor<Map<String, Object>> saved = ArgumentCaptor.forClass(Map.class);
        verify(tools).saveRecord(eq(7L), eq("JOB_DESCRIPTION"), anyString(), eq("Analyzed"), saved.capture());
        assertEquals(result, saved.getValue());
    }
}
