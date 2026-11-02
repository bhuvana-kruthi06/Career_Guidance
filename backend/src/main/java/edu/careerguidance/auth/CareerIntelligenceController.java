package edu.careerguidance.auth;

import edu.careerguidance.service.CareerCatalogService;
import edu.careerguidance.service.ResourceCatalogService;
import edu.careerguidance.service.ResumeAnalyzerService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/me")
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class CareerIntelligenceController {
    private static final long MAX_PDF_BYTES = 5L * 1024 * 1024;
    private final CareerIntelligenceRepository intelligence;
    private final AccountDataRepository accountData;
    private final CareerCatalogService careers;
    private final ResourceCatalogService resources;
    private final ResumeAnalyzerService analyzer;

    public CareerIntelligenceController(CareerIntelligenceRepository intelligence, AccountDataRepository accountData,
            CareerCatalogService careers, ResourceCatalogService resources, ResumeAnalyzerService analyzer) {
        this.intelligence = intelligence; this.accountData = accountData; this.careers = careers; this.resources = resources; this.analyzer = analyzer;
    }

    @PostMapping(path = "/resumes", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Map<String, Object> upload(@AuthenticationPrincipal CurrentUser user, @RequestPart("file") MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > MAX_PDF_BYTES) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a PDF file smaller than 5 MB.");
        if (file.getOriginalFilename() == null || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".pdf"))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a PDF resume.");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D' || bytes[3] != 'F' || bytes[4] != '-')
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The selected file is not a valid PDF.");
            var target = intelligence.targetRole(user.id());
            String roleCode = target.map(value -> String.valueOf(value.get("code"))).orElse(null);
            Map<String, Object> result = analyzer.analyze(bytes, roleCode);
            Map<String, Object> extracted = (Map<String, Object>) result.get("extracted");
            Map<String, Object> report = (Map<String, Object>) result.get("report");
            return intelligence.saveResume(user.id(), roleCode, extracted, report);
        } catch (java.io.IOException ex) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The PDF could not be read."); }
    }

    @GetMapping("/resumes") public List<Map<String, Object>> resumes(@AuthenticationPrincipal CurrentUser user) { return intelligence.recentResumes(user.id()); }
    @GetMapping("/resumes/{id}") public Map<String, Object> resume(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        return intelligence.resume(user.id(), id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume analysis was not found."));
    }
    @PutMapping("/resumes/{id}/apply-to-profile") public Map<String, Object> applyResume(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        Map<String, Object> extracted = intelligence.resume(user.id(), id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Resume analysis was not found."));
        Map<String, Object> fields = (Map<String, Object>) extracted.get("extracted");
        Map<String, Object> profile = new HashMap<>(intelligence.profile(user.id())); int changed = 0;
        changed += setMissing(profile, "name", fields.get("name")); changed += setMissing(profile, "email", fields.get("email"));
        changed += setMissing(profile, "phone", fields.get("phone")); changed += setMissing(profile, "linkedin", fields.get("linkedIn"));
        changed += setMissing(profile, "github", fields.get("github"));
        changed += setMissing(profile, "education", joinLines(fields.get("education"), 254));
        changed += setMissing(profile, "resumeSkills", fields.get("skills")); changed += setMissing(profile, "projects", fields.get("projects"));
        changed += setMissing(profile, "experience", joinLines(fields.get("experience"), 2000));
        changed += setMissing(profile, "internships", fields.get("internships")); changed += setMissing(profile, "certifications", fields.get("certifications"));
        accountData.saveProfile(user.id(), profile);
        return Map.of("updatedFields", changed, "profile", profile);
    }

    @GetMapping("/skill-gaps") public Map<String, Object> skillGaps(@AuthenticationPrincipal CurrentUser user) { return skillGapReport(user.id()); }
    @PutMapping("/skill-gaps/progress") public Map<String, Object> updateProgress(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SkillProgressRequest body) {
        Map<String, Object> role = careers.detail(body.roleCode());
        if (role == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Career role was not found.");
        boolean known = requirements(role).stream().anyMatch(item -> body.skillName().equalsIgnoreCase(String.valueOf(item.get("element_name"))));
        if (!known) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This skill is not part of the selected role.");
        var target = intelligence.targetRole(user.id());
        if (target.isEmpty() || !body.roleCode().equals(target.get().get("code"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "Select this role as your target before updating progress.");
        intelligence.updateSkillProgress(user.id(), body.roleCode(), body.skillName(), body.status());
        return skillGapReport(user.id());
    }

    Map<String, Object> skillGapReport(long userId) {
        var target = intelligence.targetRole(userId);
        if (target.isEmpty()) return Map.of("targetRole", Map.of(), "skills", List.of(), "completionPercent", 0, "total", 0, "learned", 0, "learning", 0);
        String code = String.valueOf(target.get().get("code")); Map<String, Object> role = careers.detail(code);
        if (role == null) return Map.of("targetRole", target.get(), "skills", List.of(), "completionPercent", 0, "total", 0, "learned", 0, "learning", 0);
        Map<String, Object> profile = intelligence.profile(userId);
        Map<String, Integer> current = numericSkills(profile.get("skills"));
        Map<String, Object> latest = intelligence.latestResume(userId).orElse(Map.of());
        Map<String, Object> latestExtracted = latest.get("extracted") instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        java.util.Set<String> resumeSkills = latestExtracted.get("skills") instanceof List<?> list ? list.stream()
                .filter(String.class::isInstance).map(String.class::cast).map(name -> name.toLowerCase(Locale.ROOT)).collect(java.util.stream.Collectors.toSet()) : java.util.Set.of();
        Map<String, String> progress = intelligence.skillProgress(userId, code);
        List<Map<String, Object>> result = new ArrayList<>(); int learned = 0, learning = 0;
        for (Map<String, Object> requirement : requirements(role)) {
            String name = String.valueOf(requirement.get("element_name")); double required = number(requirement.get("required_level"));
            double importance = number(requirement.get("importance")); int currentLevel = 0;
            for (var entry : current.entrySet()) if (entry.getKey().equalsIgnoreCase(name)) currentLevel = (int) Math.round((entry.getValue() - 1) * 7.0 / 4.0);
            int gap = Math.max(0, (int) Math.ceil(required - currentLevel));
            boolean resumeMentioned = resumeSkills.contains(name.toLowerCase(Locale.ROOT));
            String priority = gap >= 3 && importance >= 3.5 ? "Critical" : gap > 0 || importance >= 3.5 ? "Important" : "Nice to have";
            String status = progress.getOrDefault(name.toLowerCase(Locale.ROOT), "Not started");
            if (status.equals("Learned")) learned++; else if (status.equals("Learning")) learning++;
            String category = category(String.valueOf(requirement.getOrDefault("skill_category", "")), name);
            List<Map<String, Object>> matchingResources = resources.all().stream().filter(item -> String.valueOf(item.getOrDefault("skill", "")).equalsIgnoreCase(name)).limit(3).toList();
            result.add(Map.of("name", name, "category", category, "currentLevel", currentLevel, "requiredLevel", required,
                    "gap", gap, "importance", importance, "priority", priority, "status", status, "resources", matchingResources,
                    "resumeMentioned", resumeMentioned));
        }
        result.sort(java.util.Comparator.comparingInt((Map<String, Object> item) -> priorityRank(String.valueOf(item.get("priority"))))
                .thenComparing((a, b) -> Integer.compare((int) b.get("gap"), (int) a.get("gap"))));
        int total = result.size();
        return Map.of("targetRole", target.get(), "skills", result, "completionPercent", total == 0 ? 0 : (int) Math.round(learned * 100.0 / total),
                "total", total, "learned", learned, "learning", learning);
    }

    private static List<Map<String, Object>> requirements(Map<String, Object> role) { return role.get("requirements") instanceof List<?> list ? list.stream().filter(Map.class::isInstance).map(item -> (Map<String, Object>) item).toList() : List.of(); }
    private static Map<String, Integer> numericSkills(Object value) {
        Map<String, Integer> result = new HashMap<>();
        if (value instanceof Map<?, ?> map) for (var item : map.entrySet()) if (item.getKey() instanceof String key && item.getValue() instanceof Number number) result.put(key, number.intValue());
        return result;
    }
    private static int setMissing(Map<String, Object> profile, String key, Object value) {
        if (value == null || value instanceof String text && text.isBlank() || value instanceof List<?> list && list.isEmpty()) return 0;
        Object old = profile.get(key); if (old != null && (!(old instanceof String text) || !text.isBlank()) && (!(old instanceof List<?> list) || !list.isEmpty())) return 0;
        profile.put(key, value); return 1;
    }
    private static String joinLines(Object value, int limit) {
        if (!(value instanceof List<?> list)) return "";
        String joined = list.stream().filter(String.class::isInstance).map(String.class::cast).limit(30)
                .collect(java.util.stream.Collectors.joining("\n"));
        return joined.substring(0, Math.min(limit, joined.length()));
    }
    private static String category(String source, String name) {
        String text = (source + " " + name).toLowerCase(Locale.ROOT);
        if (text.contains("database") || text.contains("sql")) return "Database";
        if (text.contains("cloud")) return "Cloud";
        if (text.contains("machine learning") || text.contains("artificial intelligence") || text.contains("ai/ml")) return "AI/ML";
        if (text.contains("front end") || text.contains("frontend") || text.contains("web design")) return "Frontend";
        if (text.contains("back end") || text.contains("backend") || text.contains("programming")) return "Backend";
        if (text.contains("interpersonal") || text.contains("communication") || text.contains("leadership")) return "Soft skills";
        return "Tools";
    }
    private static double number(Object value) { return value instanceof Number number ? number.doubleValue() : 0; }
    private static int priorityRank(String value) { return switch (value) { case "Critical" -> 0; case "Important" -> 1; default -> 2; }; }

    public record SkillProgressRequest(@NotBlank @Pattern(regexp="[0-9]{2}-[0-9]{4}\\.[0-9]{2}") String roleCode,
            @NotBlank @Size(max=120) String skillName, @NotBlank @Pattern(regexp="Learning|Learned") String status) { }
}
