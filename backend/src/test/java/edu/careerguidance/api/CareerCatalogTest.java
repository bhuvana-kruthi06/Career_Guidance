package edu.careerguidance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.hasSize;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import edu.careerguidance.service.MatcherBridge;

@SpringBootTest(properties = "career-guidance.project-root=.")
@AutoConfigureMockMvc
class CareerCatalogTest {
    @Autowired org.springframework.test.web.servlet.MockMvc mvc;
    @MockitoBean MatcherBridge matcher;

    @Test void searchesCareerMetadataAndEnforcesPageCaps() throws Exception {
        mvc.perform(get("/api/careers").param("query", "Software Developer").param("limit", "10"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.results[0].onet_soc_code").value("15-1252.00"));
        mvc.perform(get("/api/careers").param("limit", "51")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/careers").param("query", "x".repeat(81))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/careers").param("riasec", "Z")).andExpect(status().isBadRequest());
    }

    @Test void returnsOccupationSkillRequirementsFromLocalMetadata() throws Exception {
        mvc.perform(get("/api/careers/15-1252.00")).andExpect(status().isOk())
                .andExpect(jsonPath("$.occupation_title").value("Software Developers"))
                .andExpect(jsonPath("$.requirements[0].element_name").isNotEmpty());
    }

    @Test void servesTheCuratedFreeResourceMapUsingHttpsLinks() throws Exception {
        mvc.perform(get("/api/resources")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(25)))
                .andExpect(jsonPath("$[0].url").value(org.hamcrest.Matchers.startsWith("https://")))
                .andExpect(jsonPath("$[0].estimated_hours").isNumber()).andExpect(jsonPath("$[0].cost").value("Free"));
    }
}
