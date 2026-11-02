package edu.careerguidance.api;

import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import edu.careerguidance.service.MatcherBridge;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class WhatIfControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @MockitoBean MatcherBridge matcher;

    @Test void comparesTwoDirectMatcherRunsAndReturnsRankDifferences() throws Exception {
        var before = JsonNodeFactory.instance.arrayNode(); before.addObject().put("rank", 1).put("onet_soc_code", "15-1252.00")
                .put("occupation_title", "Software Developers").put("match_score", 71.0);
        var after = JsonNodeFactory.instance.arrayNode(); after.addObject().put("rank", 1).put("onet_soc_code", "15-1252.00")
                .put("occupation_title", "Software Developers").put("match_score", 82.0);
        when(matcher.match(anyMap())).thenReturn(before, after);
        mvc.perform(post("/api/what-if").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":{\"studentSkills\":{\"Reading\":3,\"Writing\":2,\"Math\":4},\"studentInterests\":[\"I\"],\"topN\":5,\"assessedOnly\":true},\"changes\":{\"Writing\":5}}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.baseline[0].match_score").value(71.0))
                .andExpect(jsonPath("$.scenario[0].match_score").value(82.0))
                .andExpect(jsonPath("$.comparison[0].rank_change").value(0));
        var calls = org.mockito.ArgumentCaptor.forClass(Map.class);
        verify(matcher, times(2)).match(calls.capture());
        @SuppressWarnings("unchecked") Map<String, Object> first = calls.getAllValues().get(0);
        @SuppressWarnings("unchecked") Map<String, Object> second = calls.getAllValues().get(1);
        org.junit.jupiter.api.Assertions.assertEquals(Map.of("Reading", 3, "Writing", 2, "Math", 4), first.get("student_skills"));
        org.junit.jupiter.api.Assertions.assertEquals(Map.of("Reading", 3, "Writing", 5, "Math", 4), second.get("student_skills"));
        org.junit.jupiter.api.Assertions.assertEquals(5, first.get("top_n"));
        org.junit.jupiter.api.Assertions.assertEquals(5, second.get("top_n"));
    }

    @Test void rejectsUnknownSkillsAndDoesNotRunTheMatcher() throws Exception {
        mvc.perform(post("/api/what-if").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"profile\":{\"studentSkills\":{\"Reading\":3,\"Writing\":2,\"Math\":4},\"studentInterests\":[\"I\"]},\"changes\":{\"Unknown\":5}}"))
                .andExpect(status().isBadRequest());
        org.mockito.Mockito.verifyNoInteractions(matcher);
    }
}
