package edu.careerguidance.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import edu.careerguidance.service.CareerCatalogService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me")
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class AccountDataController {
    private final AccountDataRepository data;
    private final CareerCatalogService careers;
    public AccountDataController(AccountDataRepository data, CareerCatalogService careers) { this.data = data; this.careers = careers; }

    @GetMapping("/profile") public Map<String, Object> profile(@AuthenticationPrincipal CurrentUser user) {
        return data.profile(user.id()).orElse(Map.of());
    }
    @PutMapping("/profile") public Map<String, Object> saveProfile(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ProfileRequest body) {
        validateProfile(body.profile());
        data.saveProfile(user.id(), body.profile()); return body.profile();
    }
    @GetMapping("/assessments/latest") public Map<String, Object> latestAssessment(@AuthenticationPrincipal CurrentUser user) {
        return data.latestAssessment(user.id()).orElse(Map.of());
    }
    @GetMapping("/recommendations") public List<?> recommendations(@AuthenticationPrincipal CurrentUser user) {
        return data.latestAssessment(user.id()).map(value -> value.get("recommendations") instanceof List<?> list ? list : List.of()).orElse(List.of());
    }
    @PostMapping("/assessments") public Map<String, Object> saveAssessment(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody AssessmentRequest body) {
        return data.saveAssessment(user.id(), body.assessment(), body.recommendations());
    }
    @PutMapping("/target-role") public Map<String, Object> selectTargetRole(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody TargetRoleRequest body) {
        Map<String, Object> career = careers.detail(body.onetSocCode());
        if (career == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Career role was not found.");
        data.selectTargetRole(user.id(), body.onetSocCode(), String.valueOf(career.get("occupation_title")));
        return Map.of("code", body.onetSocCode(), "title", career.get("occupation_title"));
    }
    @GetMapping("/sessions") public List<Map<String, Object>> sessions(@AuthenticationPrincipal CurrentUser user) { return data.sessions(user.id()); }
    @GetMapping("/sessions/{id}") public Map<String, Object> session(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        return data.session(user.id(), id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
    @DeleteMapping("/sessions/{id}") public void deleteSession(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        if (!data.deleteSession(user.id(), id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    @GetMapping("/saved-careers") public List<Map<String, Object>> saved(@AuthenticationPrincipal CurrentUser user) { return data.saved(user.id()); }
    @PostMapping("/saved-careers") public void saveCareer(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody CareerRequest body) {
        data.saveCareer(user.id(), body.onetSocCode(), body.career());
    }
    @DeleteMapping("/saved-careers/{code}") public void removeCareer(@AuthenticationPrincipal CurrentUser user, @PathVariable @Pattern(regexp = "[0-9]{2}-[0-9]{4}\\.[0-9]{2}") String code) {
        if (!data.removeCareer(user.id(), code)) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    }
    @PutMapping("/roadmap") public void replaceRoadmap(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody RoadmapRequest body) {
        data.replaceRoadmap(user.id(), body.items());
    }
    @PostMapping("/import-local") @Transactional public Map<String, Object> importLocal(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ImportRequest body) {
        if (data.imported(user.id())) return Map.of("imported", false, "reason", "already_imported");
        if (body.profile() != null) data.markImported(user.id(), body.profile());
        if (body.savedCareers() != null) for (Map<String, Object> career : body.savedCareers()) {
            Object code = career.get("onet_soc_code"); if (code instanceof String soc && soc.matches("[0-9]{2}-[0-9]{4}\\.[0-9]{2}")) data.saveCareer(user.id(), soc, career);
        }
        if (body.roadmap() != null) data.replaceRoadmap(user.id(), body.roadmap());
        if (body.skills() != null && body.results() != null && !body.results().isEmpty()) {
            data.saveMatch(user.id(), body.skills(), body.interests() == null ? List.of() : body.interests(), body.results());
        }
        if (body.profile() == null) data.markImported(user.id(), Map.of());
        return Map.of("imported", true);
    }
    @DeleteMapping @Transactional public void deleteAccount(@AuthenticationPrincipal CurrentUser user) { data.deleteAccount(user.id()); }

    public record ProfileRequest(@NotNull @Size(max=40) Map<@NotBlank @Size(max=80) String, Object> profile) { }
    public record AssessmentRequest(@NotNull Map<@NotBlank @Size(max=80) String, Object> assessment,
                                    @NotNull @Size(max=25) List<Map<@NotBlank @Size(max=80) String, Object>> recommendations) { }
    public record TargetRoleRequest(@NotBlank @Pattern(regexp="[0-9]{2}-[0-9]{4}\\.[0-9]{2}") String onetSocCode) { }
    public record CareerRequest(@NotBlank @Pattern(regexp="[0-9]{2}-[0-9]{4}\\.[0-9]{2}") String onetSocCode,
                                @Valid Map<@NotBlank @Size(max=80) String, Object> career) { }
    public record RoadmapRequest(@NotNull @Size(max=100) List<Map<@NotBlank @Size(max=120) String, Object>> items) { }
    public record ImportRequest(Map<String, Object> profile, @Size(max=100) List<Map<String,Object>> savedCareers,
                                @Size(max=100) List<Map<String,Object>> roadmap, Map<String, Integer> skills,
                                @Size(max=3) List<String> interests, @Size(max=25) List<Map<String,Object>> results) { }

    private void validateProfile(Map<String, Object> profile) {
        for (String field : List.of("name", "email", "education", "stage", "studyArea", "college", "graduationYear",
                "experience", "expectedSalary", "weeklyLearningHours", "shortTermGoal", "longTermGoal")) {
            Object value = profile.get(field);
            int maximum = field.equals("experience") || field.endsWith("Goal") ? 2000 : 254;
            if (value != null && (!(value instanceof String text) || text.length() > maximum))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile field '" + field + "' has an invalid value or is too long.");
        }
        for (String field : List.of("projects", "internships", "certifications", "targetCompanies", "preferredLocations", "preferredRoles", "customRoles")) {
            Object value = profile.get(field);
            if (value != null && (!(value instanceof List<?> list) || list.size() > 50 || list.stream().anyMatch(item -> !(item instanceof String text) || text.length() > 500)))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Profile list '" + field + "' contains invalid entries.");
        }
        Object rawSkills = profile.get("skills");
        if (rawSkills != null) {
            if (!(rawSkills instanceof Map<?, ?> skills) || skills.size() > 100 || skills.entrySet().stream().anyMatch(entry ->
                    !(entry.getKey() instanceof String key) || key.isBlank() || key.length() > 120
                            || !(entry.getValue() instanceof Number rating) || rating.intValue() < 1 || rating.intValue() > 5))
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rate up to 100 profile skills from 1 to 5.");
        }
        Object rawInterests = profile.get("interests");
        if (rawInterests != null && (!(rawInterests instanceof List<?> values) || values.size() > 6
                || values.stream().anyMatch(value -> !(value instanceof String))))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose up to six interests.");
    }
}
