package edu.careerguidance.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "career-guidance.project-root=..",
        "career-guidance.python-executable=venv/Scripts/python.exe"
})
@AutoConfigureMockMvc
class MatchControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void rejectsRatingsOutsideOneToFive() throws Exception {
        mvc.perform(post("/api/matches").contentType(MediaType.APPLICATION_JSON)
                .content("{\"studentSkills\":{\"Mathematics\":6}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returnsRealMatchesFromThePythonEngine() throws Exception {
        var skillsResponse = mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/skills"))
                .andReturn();
        if (skillsResponse.getResponse().getStatus() != 200) {
            throw new AssertionError("Skill endpoint failed: " + skillsResponse.getResponse().getContentAsString());
        }
        JsonNode skills = mapper.readTree(skillsResponse.getResponse().getContentAsString());
        Map<String, Integer> ratings = new LinkedHashMap<>();
        skills.forEach(skill -> ratings.put(skill.path("name").asText(), 5));
        ObjectNode request = mapper.createObjectNode();
        request.set("studentSkills", mapper.valueToTree(ratings));
        request.set("studentInterests", mapper.createArrayNode().add("I"));
        request.put("topN", 2);
        request.put("assessedOnly", true);

        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/matches")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(request)))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].occupation_title").isNotEmpty())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$[0].match_score").value(100.0));
    }
}
