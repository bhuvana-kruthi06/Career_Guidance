package edu.careerguidance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.careerguidance.api.MatchRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/** Builds role guidance from the installed O*NET matcher and curated local skill resources. */
@Service
public class CareerRecommendationService {
    private static final Set<String> RIASEC = Set.of("R", "I", "A", "S", "E", "C");
    private final MatcherBridge matcher;
    private final CareerCatalogService careers;
    private final ResourceCatalogService resources;
    private final ObjectMapper mapper;

    public CareerRecommendationService(MatcherBridge matcher, CareerCatalogService careers,
            ResourceCatalogService resources, ObjectMapper mapper) {
        this.matcher = matcher; this.careers = careers; this.resources = resources; this.mapper = mapper;
    }

    public List<Map<String, Object>> recommend(MatchRequest request) {
        if (request.getStudentSkills() == null || request.getStudentSkills().size() < 3)
            throw new IllegalArgumentException("Rate at least three skills to get recommendations.");
        if (request.getStudentInterests() == null || request.getStudentInterests().isEmpty()
                || request.getStudentInterests().size() > 3 || request.getStudentInterests().stream()
                .anyMatch(code -> code == null || !RIASEC.contains(code.trim().toUpperCase(Locale.ROOT))))
            throw new IllegalArgumentException("Choose between one and three valid work interests.");
        JsonNode matches = matcher.match(Map.of("student_skills", request.getStudentSkills(),
                "student_interests", request.getStudentInterests(), "top_n", request.getTopN() == null ? 10 : request.getTopN(),
                "assessed_only", true));
        List<Map<String, Object>> result = mapper.convertValue(matches, new TypeReference<>() {});
        for (Map<String, Object> role : result) enrich(role, request.getStudentSkills(), request.getStudentInterests(), request.getAssessment());
        return result;
    }

    private void enrich(Map<String, Object> role, Map<String, Integer> ratings, List<String> interests, Map<String, Object> assessment) {
        String code = String.valueOf(role.get("onet_soc_code"));
        Map<String, Object> detail = careers.detail(code);
        List<Map<String, Object>> requirements = detail == null ? List.of() : castMapList(detail.get("requirements"));
        List<String> strong = castStringList(role.get("strong_skills"));
        List<Map<String, Object>> gaps = castMapList(role.get("skill_gaps"));
        List<String> missing = gaps.stream().map(gap -> String.valueOf(gap.get("element_name"))).toList();
        String title = String.valueOf(role.get("occupation_title"));
        List<String> strengths = strong.stream().limit(3).toList();
        role.put("required_skills", requirements.stream().limit(15).map(skill -> Map.of(
                "name", String.valueOf(skill.get("element_name")), "importance", skill.getOrDefault("importance", 0),
                "requiredLevel", skill.getOrDefault("required_level", 0))).toList());
        role.put("current_skill_coverage", Map.of("assessed", role.getOrDefault("skills_assessed", 0),
                "required", role.getOrDefault("skills_total", 0), "strongSkills", strong));
        role.put("missing_skills", gaps);
        List<String> fitSignals = new ArrayList<>();
        if (!strengths.isEmpty()) fitSignals.add("your strengths in " + String.join(", ", strengths));
        if (!interests.isEmpty()) fitSignals.add("your selected work interests");
        for (String key : List.of("workStyle", "environment", "technicalComfort", "careerGoal")) {
            Object value = assessment.get(key);
            if (value instanceof List<?> list && !list.isEmpty()) fitSignals.add("your assessment preference for " + String.join(", ", list.stream().map(String::valueOf).limit(2).toList()));
            else if (value instanceof String text && !text.isBlank()) fitSignals.add("your assessment preference for " + text);
        }
        Object preferred = assessment.get("preferredRoles");
        if (preferred instanceof List<?> list && list.stream().map(String::valueOf)
                .anyMatch(value -> title.toLowerCase(Locale.ROOT).contains(value.toLowerCase(Locale.ROOT))))
            fitSignals.add("your preferred role selection");
        role.put("why_this_fits", fitSignals.isEmpty() ? "This role aligns with the current O*NET profile signals."
                : "This role aligns with " + String.join("; ", fitSignals) + ".");
        role.put("example_job_titles", List.of(title));
        role.put("career_progression_path", List.of("Explore entry-level opportunities in " + title,
                "Build experience in the role’s core skills", "Progress toward senior or specialist responsibilities"));
        role.put("growth_path_basis", "Illustrative planning sequence; this occupation-specific progression is not included in the available source data.");
        List<String> projectSkills = new ArrayList<>(missing);
        if (projectSkills.isEmpty()) projectSkills.addAll(strengths);
        role.put("recommended_projects", projectSkills.stream().limit(3)
                .map(skill -> Map.of("title", "Portfolio project: " + skill, "skill", skill,
                        "description", "Create a small, documented project that demonstrates " + skill + " in a context related to " + title + "."))
                .toList());
        List<Map<String, Object>> learning = new ArrayList<>();
        for (Map<String, Object> gap : gaps.stream().limit(5).toList()) {
            String skill = String.valueOf(gap.get("element_name"));
            resources.all().stream().filter(item -> skill.equalsIgnoreCase(String.valueOf(item.get("skill"))))
                    .findFirst().ifPresent(item -> learning.add(Map.of("skill", skill, "provider", item.getOrDefault("provider", "Curated resource"),
                            "url", item.getOrDefault("url", ""), "level", item.getOrDefault("level", "Not specified"),
                            "estimated_hours", item.getOrDefault("estimated_hours", 0), "cost", item.getOrDefault("cost", "Not specified"))));
        }
        role.put("learning_resources", learning);
        role.put("data_basis", "O*NET career and skill data, profile ratings, and selected interests");
    }

    @SuppressWarnings("unchecked") private static List<Map<String, Object>> castMapList(Object value) {
        return value instanceof List<?> list ? (List<Map<String, Object>>) (List<?>) list : List.of();
    }
    @SuppressWarnings("unchecked") private static List<String> castStringList(Object value) {
        return value instanceof List<?> list ? (List<String>) (List<?>) list : List.of();
    }
}
