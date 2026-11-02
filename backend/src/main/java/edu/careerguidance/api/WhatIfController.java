package edu.careerguidance.api;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import edu.careerguidance.service.MatcherBridge;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/what-if")
public class WhatIfController {
    private static final Set<String> RIASEC = Set.of("R", "I", "A", "S", "E", "C");
    private final MatcherBridge matcher;
    private final com.fasterxml.jackson.databind.ObjectMapper mapper;
    public WhatIfController(MatcherBridge matcher, com.fasterxml.jackson.databind.ObjectMapper mapper) { this.matcher = matcher; this.mapper = mapper; }

    @PostMapping public Map<String, Object> simulate(@Valid @RequestBody WhatIfRequest request) {
        MatchRequest profile = request.profile();
        if (profile.getStudentSkills() == null || profile.getStudentSkills().size() < 3)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rate at least three skills before trying a what-if scenario.");
        if (profile.getStudentInterests() == null || profile.getStudentInterests().isEmpty() || profile.getStudentInterests().size() > 3
                || profile.getStudentInterests().stream().anyMatch(code -> code == null || !RIASEC.contains(code.toUpperCase())))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose between one and three valid work interests.");
        if (!profile.getStudentSkills().keySet().containsAll(request.changes().keySet()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Change only skills included in your current profile.");
        Map<String, Object> baselineRequest = input(profile.getStudentSkills(), profile);
        Map<String, Integer> changedSkills = new HashMap<>(profile.getStudentSkills()); changedSkills.putAll(request.changes());
        Map<String, Object> scenarioRequest = input(changedSkills, profile);
        List<Map<String, Object>> baseline = results(matcher.match(baselineRequest));
        List<Map<String, Object>> scenario = results(matcher.match(scenarioRequest));
        Map<String, Integer> baselineRanks = new HashMap<>();
        for (Map<String, Object> career : baseline) baselineRanks.put(String.valueOf(career.get("onet_soc_code")), integer(career.get("rank")));
        List<Map<String, Object>> compared = new ArrayList<>();
        for (Map<String, Object> career : scenario) {
            String code = String.valueOf(career.get("onet_soc_code")); Integer oldRank = baselineRanks.get(code);
            Map<String, Object> item = new HashMap<>(); item.put("onet_soc_code", code); item.put("occupation_title", career.get("occupation_title"));
            item.put("baseline_rank", oldRank); item.put("scenario_rank", integer(career.get("rank")));
            item.put("rank_change", oldRank == null ? null : oldRank - integer(career.get("rank")));
            item.put("baseline_score", baseline.stream().filter(row -> code.equals(String.valueOf(row.get("onet_soc_code")))).findFirst().map(row -> row.get("match_score")).orElse(null));
            item.put("scenario_score", career.get("match_score")); compared.add(item);
        }
        return Map.of("changes", request.changes(), "baseline", baseline, "scenario", scenario, "comparison", compared);
    }

    private static Map<String, Object> input(Map<String, Integer> skills, MatchRequest request) {
        Map<String, Object> body = new HashMap<>(); body.put("student_skills", skills); body.put("student_interests", request.getStudentInterests());
        body.put("top_n", 5); body.put("assessed_only", request.isAssessedOnly()); return body;
    }
    private List<Map<String, Object>> results(JsonNode node) {
        if (!node.isArray()) throw new IllegalStateException("Matcher returned an invalid what-if result.");
        return mapper.convertValue(node, new TypeReference<>() {});
    }
    private static Integer integer(Object value) { try { return Integer.valueOf(String.valueOf(value)); } catch (Exception e) { return null; } }
}
