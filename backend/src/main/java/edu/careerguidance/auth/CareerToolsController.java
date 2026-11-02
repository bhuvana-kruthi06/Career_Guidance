package edu.careerguidance.auth;

import edu.careerguidance.service.CareerAssistantService;
import edu.careerguidance.service.CareerCatalogService;
import edu.careerguidance.service.ResourceCatalogService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.validation.annotation.Validated;

@RestController
@RequestMapping("/api/me")
@Validated
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class CareerToolsController {
    private final CareerToolsRepository tools;
    private final CareerIntelligenceRepository intelligence;
    private final CareerIntelligenceController careerIntelligence;
    private final AccountDataRepository accounts;
    private final CareerCatalogService careers;
    private final ResourceCatalogService resources;
    private final CareerAssistantService assistant;

    public CareerToolsController(CareerToolsRepository tools, CareerIntelligenceRepository intelligence,
            CareerIntelligenceController careerIntelligence, AccountDataRepository accounts, CareerCatalogService careers,
            ResourceCatalogService resources, CareerAssistantService assistant) {
        this.tools = tools; this.intelligence = intelligence; this.careerIntelligence = careerIntelligence;
        this.accounts = accounts; this.careers = careers; this.resources = resources; this.assistant = assistant;
    }

    @GetMapping("/roadmap") public Map<String, Object> roadmap(@AuthenticationPrincipal CurrentUser user) {
        return tools.record(user.id(), "ROADMAP", "active").map(record -> assistant.decorateRoadmap(object(record.get("data"))))
                .orElseGet(() -> {
                    Map<String, Object> empty = new LinkedHashMap<>();
                    empty.put("generated", false); empty.put("phases", List.of()); empty.put("completionPercent", 0);
                    intelligence.targetRole(user.id()).ifPresentOrElse(target -> {
                        empty.put("hasTargetRole", true); empty.put("targetRole", target); empty.put("roleTitle", target.get("title"));
                    }, () -> empty.put("hasTargetRole", false));
                    return empty;
                });
    }
    @PostMapping("/roadmap") public Map<String, Object> generateRoadmap(@AuthenticationPrincipal CurrentUser user) {
        Map<String, Object> profile = intelligence.profile(user.id()); Map<String, Object> target = intelligence.targetRole(user.id()).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Select a target role before generating a roadmap."));
        Map<String, Object> gaps = careerIntelligence.skillGapReport(user.id()); Map<String, Object> resume = intelligence.latestResume(user.id()).orElse(Map.of());
        Map<String, Object> assessment = accounts.latestAssessment(user.id()).orElse(Map.of());
        Map<String, Object> generated = assistant.generateRoadmap(profile, target, gaps, resume, assessment);
        Map<String, Object> current = tools.record(user.id(), "ROADMAP", "active").map(r -> object(r.get("data"))).orElse(Map.of());
        preserveRoadmapProgress(current, generated); generated.put("generated", true); generated.put("hasTargetRole", true);
        generated.put("targetRole", target); generated.put("updatedAt", java.time.Instant.now().toString());
        tools.saveRecord(user.id(), "ROADMAP", "active", "Active", generated); return generated;
    }
    @PutMapping("/roadmap/progress") public Map<String, Object> updateRoadmap(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody RoadmapProgressRequest body) {
        Map<String, Object> roadmap = tools.record(user.id(), "ROADMAP", "active").map(r -> object(r.get("data")))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Generate a roadmap before updating its progress."));
        boolean found = false;
        for (Map<String, Object> phase : mapList(roadmap.get("phases"))) {
            if (phase.get("id").equals(body.phaseId())) {
                if ("milestone".equals(body.itemType())) {
                    Map<String, Object> milestone = object(phase.get("milestone"));
                    if (!body.itemId().equals(milestone.get("id"))) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Roadmap milestone was not found.");
                    milestone.put("complete", body.complete()); phase.put("milestone", milestone); found = true;
                } else {
                    for (Map<String, Object> week : mapList(phase.get("weeks"))) for (Map<String, Object> task : mapList(week.get("tasks")))
                        if (body.itemId().equals(task.get("id"))) { task.put("complete", body.complete()); found = true; }
                }
            }
        }
        if (!found) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Roadmap item was not found.");
        roadmap.put("updatedAt", java.time.Instant.now().toString()); assistant.decorateRoadmap(roadmap);
        tools.saveRecord(user.id(), "ROADMAP", "active", "Active", roadmap); return roadmap;
    }

    @GetMapping("/projects") public Map<String, Object> projects(@AuthenticationPrincipal CurrentUser user) {
        Map<String, Object> target = intelligence.targetRole(user.id()).orElse(null);
        if (target == null) return Map.of("targetRole", Map.of(), "projects", List.of());
        List<Map<String, Object>> gaps = mapList(careerIntelligence.skillGapReport(user.id()).get("skills"));
        List<Map<String, Object>> recommendations = assistant.recommendProjects(String.valueOf(target.get("title")), gaps);
        Map<String, Map<String, Object>> saved = new HashMap<>();
        for (Map<String, Object> record : tools.records(user.id(), "PROJECT", 100)) saved.put(String.valueOf(record.get("key")), object(record.get("data")));
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> project : recommendations) {
            Map<String, Object> value = new LinkedHashMap<>(project); Map<String, Object> existing = saved.get(String.valueOf(project.get("projectKey")));
            value.put("status", existing == null ? "Recommended" : existing.get("status")); value.put("saved", existing != null); result.add(value);
        }
        for (var entry : saved.entrySet()) if (result.stream().noneMatch(p -> entry.getKey().equals(p.get("projectKey")))) result.add(entry.getValue());
        return Map.of("targetRole", target, "projects", result);
    }
    @PostMapping("/projects") public Map<String, Object> saveProject(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody SaveProjectRequest body) {
        Map<String, Object> project = projects(user).get("projects") instanceof List<?> list ? list.stream().filter(Map.class::isInstance).map(x -> (Map<String, Object>) x).filter(p -> body.projectKey().equals(p.get("projectKey"))).findFirst().orElse(null) : null;
        if (project == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Recommended project was not found.");
        project.put("saved", true); project.put("status", "Planned"); tools.saveRecord(user.id(), "PROJECT", body.projectKey(), "Planned", project); return project;
    }
    @PutMapping("/projects/{projectKey}/status") public Map<String, Object> projectStatus(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Pattern(regexp="[a-z0-9-]{3,80}") String projectKey, @Valid @RequestBody ProjectStatusRequest body) {
        Map<String, Object> existing = tools.record(user.id(), "PROJECT", projectKey).map(r -> object(r.get("data")))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Save this project before updating its status."));
        existing.put("status", body.status()); existing.put("saved", true);
        tools.saveRecord(user.id(), "PROJECT", projectKey, body.status(), existing);
        if ("Completed".equals(body.status())) completePortfolioRoadmapTask(user.id());
        return existing;
    }

    @GetMapping("/mentor") public List<Map<String, Object>> mentorConversations(@AuthenticationPrincipal CurrentUser user) {
        return tools.records(user.id(), "MENTOR", 40).stream().map(record -> {
            Map<String, Object> data = object(record.get("data")); List<Map<String, Object>> messages = mapList(data.get("messages"));
            Map<String, Object> value = new LinkedHashMap<>(); value.put("conversationId", record.get("key"));
            value.put("title", data.getOrDefault("title", "Career conversation")); value.put("updatedAt", record.get("updatedAt"));
            value.put("lastMessage", messages.isEmpty() ? "" : messages.getLast().getOrDefault("content", "")); return value;
        }).toList();
    }
    @GetMapping("/mentor/{conversationId}") public Map<String, Object> mentorConversation(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Pattern(regexp="[0-9a-fA-F-]{36}") String conversationId) {
        return tools.record(user.id(), "MENTOR", conversationId).map(r -> object(r.get("data")))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mentor conversation was not found."));
    }
    @PostMapping("/mentor") public Map<String, Object> sendMentorMessage(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody MentorMessageRequest body) {
        String id = body.conversationId() == null || body.conversationId().isBlank() ? UUID.randomUUID().toString() : body.conversationId();
        Map<String, Object> conversation = tools.record(user.id(), "MENTOR", id).map(r -> object(r.get("data"))).orElseGet(LinkedHashMap::new);
        if (!conversation.isEmpty() && conversation.get("messages") == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "This conversation cannot be continued.");
        @SuppressWarnings("unchecked") List<Map<String, Object>> messages = new ArrayList<>(conversation.get("messages") instanceof List<?> list ? list.stream().filter(Map.class::isInstance).map(x -> (Map<String, Object>) x).toList() : List.of());
        String prompt = body.message().trim(); messages.add(Map.of("role", "user", "content", prompt, "createdAt", java.time.Instant.now().toString()));
        Map<String, Object> context = mentorContext(user.id());
        context.put("recentConversation", messages.stream().skip(Math.max(0, messages.size() - 8)).map(message -> Map.of("role", message.get("role"), "content", message.get("content"))).toList());
        Map<String, Object> structured = assistant.mentorReply(prompt, context);
        String answer = String.valueOf(structured.get("summary")); messages.add(Map.of("role", "assistant", "content", answer, "structured", structured, "createdAt", java.time.Instant.now().toString()));
        if (messages.size() > 100) messages = new ArrayList<>(messages.subList(messages.size() - 100, messages.size()));
        conversation.put("id", id); conversation.put("title", conversation.getOrDefault("title", prompt.length() > 52 ? prompt.substring(0, 52) + "…" : prompt));
        conversation.put("messages", messages); conversation.put("lastActivity", java.time.Instant.now().toString());
        tools.saveRecord(user.id(), "MENTOR", id, "Active", conversation);
        return Map.of("conversationId", id, "messages", List.of(messages.get(messages.size() - 2), messages.getLast()), "assistant", structured);
    }
    @DeleteMapping("/mentor/{conversationId}") public void deleteMentorConversation(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Pattern(regexp="[0-9a-fA-F-]{36}") String conversationId) {
        if (!tools.deleteRecord(user.id(), "MENTOR", conversationId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Mentor conversation was not found.");
    }

    @GetMapping("/interviews") public List<Map<String, Object>> interviews(@AuthenticationPrincipal CurrentUser user) {
        return tools.records(user.id(), "INTERVIEW", 50).stream().map(record -> {
            Map<String, Object> value = new LinkedHashMap<>(object(record.get("data")));
            value.put("sessionId", record.get("key")); value.put("status", record.get("status")); value.put("updatedAt", record.get("updatedAt"));
            return value;
        }).toList();
    }
    @PostMapping("/interviews") public Map<String, Object> startInterview(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody InterviewStartRequest body) {
        Map<String, Object> target = intelligence.targetRole(user.id()).orElse(Map.of());
        String role = body.role() == null || body.role().isBlank() ? String.valueOf(target.getOrDefault("title", "Career candidate")) : body.role().trim();
        Map<String, Object> gaps = careerIntelligence.skillGapReport(user.id());
        List<Map<String, Object>> questions = assistant.interviewQuestions(role, body.type(), body.difficulty(), mapList(gaps.get("skills")));
        String id = UUID.randomUUID().toString(); Map<String, Object> session = new LinkedHashMap<>();
        session.put("sessionId", id); session.put("role", role); session.put("type", body.type()); session.put("difficulty", body.difficulty());
        session.put("questions", questions); session.put("answers", new ArrayList<>()); session.put("currentQuestion", 0);
        session.put("readinessScore", null); session.put("status", "In Progress"); session.put("startedAt", java.time.Instant.now().toString());
        tools.saveRecord(user.id(), "INTERVIEW", id, "In Progress", session); return session;
    }
    @PostMapping("/interviews/{sessionId}/answers") public Map<String, Object> answerInterview(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Pattern(regexp="[0-9a-fA-F-]{36}") String sessionId, @Valid @RequestBody InterviewAnswerRequest body) {
        Map<String, Object> session = tools.record(user.id(), "INTERVIEW", sessionId).map(r -> object(r.get("data")))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview session was not found."));
        if (!"In Progress".equals(session.get("status"))) throw new ResponseStatusException(HttpStatus.CONFLICT, "This interview is already complete.");
        List<Map<String, Object>> questions = mapList(session.get("questions")); List<Map<String, Object>> answers = new ArrayList<>(mapList(session.get("answers")));
        int index = answers.size(); if (index >= questions.size()) throw new ResponseStatusException(HttpStatus.CONFLICT, "All interview questions have been answered.");
        Map<String, Object> question = questions.get(index); if (!String.valueOf(question.get("id")).equals(body.questionId())) throw new ResponseStatusException(HttpStatus.CONFLICT, "Answer the current interview question first.");
        Map<String, Object> feedback = assistant.evaluateAnswer(String.valueOf(question.get("prompt")), body.answer().trim(), String.valueOf(session.get("role")), String.valueOf(session.get("difficulty")));
        Map<String, Object> savedAnswer = new LinkedHashMap<>(); savedAnswer.put("questionId", body.questionId()); savedAnswer.put("question", question.get("prompt")); savedAnswer.put("answer", body.answer().trim()); savedAnswer.put("feedback", feedback);
        answers.add(savedAnswer); session.put("answers", answers); session.put("currentQuestion", answers.size());
        tools.saveRecord(user.id(), "INTERVIEW", sessionId, "In Progress", session);
        return Map.of("feedback", feedback, "currentQuestion", answers.size(), "totalQuestions", questions.size(), "complete", answers.size() == questions.size());
    }
    @PostMapping("/interviews/{sessionId}/complete") public Map<String, Object> completeInterview(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Pattern(regexp="[0-9a-fA-F-]{36}") String sessionId) {
        Map<String, Object> session = tools.record(user.id(), "INTERVIEW", sessionId).map(r -> object(r.get("data")))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Interview session was not found."));
        List<Map<String, Object>> questions = mapList(session.get("questions")); List<Map<String, Object>> answers = mapList(session.get("answers"));
        if (answers.size() != questions.size()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Answer every question before finishing the interview.");
        List<Map<String, Object>> feedback = answers.stream().map(a -> object(a.get("feedback"))).toList();
        int score = feedback.stream().mapToInt(f -> ((Number) f.getOrDefault("score", 0)).intValue()).sum() / Math.max(1, feedback.size());
        List<String> strengths = new ArrayList<>(); List<String> improvements = new ArrayList<>();
        if (feedback.stream().mapToInt(f -> ((Number) f.getOrDefault("structure", 0)).intValue()).average().orElse(0) >= 65) strengths.add("Your answers show a useful structure.");
        if (feedback.stream().mapToInt(f -> ((Number) f.getOrDefault("communication", 0)).intValue()).average().orElse(0) >= 65) strengths.add("Your answers are reasonably concise and clear.");
        for (Map<String, Object> item : feedback) for (Object missing : (List<?>) item.getOrDefault("missingPoints", List.of())) if (!improvements.contains(String.valueOf(missing))) improvements.add(String.valueOf(missing));
        if (strengths.isEmpty()) strengths.add("You completed the full practice set and have a baseline to improve from.");
        session.put("readinessScore", score); session.put("strengths", strengths); session.put("improvementAreas", improvements.stream().limit(6).toList());
        session.put("completedAt", java.time.Instant.now().toString()); session.put("status", "Completed");
        tools.saveRecord(user.id(), "INTERVIEW", sessionId, "Completed", session); return session;
    }

    @GetMapping("/job-descriptions") public List<Map<String, Object>> jobDescriptionAnalyses(@AuthenticationPrincipal CurrentUser user) {
        return tools.records(user.id(), "JOB_DESCRIPTION", 50).stream().map(record -> {
            Map<String, Object> value = new LinkedHashMap<>(object(record.get("data"))); value.put("analysisId", record.get("key")); value.put("updatedAt", record.get("updatedAt")); return value;
        }).toList();
    }
    @GetMapping("/job-descriptions/{analysisId}") public Map<String, Object> jobDescriptionAnalysis(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Pattern(regexp="[0-9a-fA-F-]{36}") String analysisId) {
        return tools.record(user.id(), "JOB_DESCRIPTION", analysisId).map(r -> object(r.get("data")))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job description analysis was not found."));
    }
    @PostMapping("/job-descriptions") public Map<String, Object> analyzeJobDescription(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody JobDescriptionRequest body) {
        Map<String, Object> profile = intelligence.profile(user.id()); Map<String, Object> target = intelligence.targetRole(user.id()).orElse(Map.of());
        Map<String, Object> resume = intelligence.latestResume(user.id()).orElse(Map.of()); Map<String, Object> gap = careerIntelligence.skillGapReport(user.id());
        Map<String, Object> analysis = new LinkedHashMap<>(assistant.analyzeJobDescription(body.description().trim(), profile, target, resume, gap));
        String id = UUID.randomUUID().toString(); analysis.put("analysisId", id); analysis.put("company", body.company() == null ? "" : body.company().trim());
        analysis.put("jobTitle", body.jobTitle() == null ? "" : body.jobTitle().trim()); analysis.put("description", body.description().trim());
        analysis.put("createdAt", java.time.Instant.now().toString()); tools.saveRecord(user.id(), "JOB_DESCRIPTION", id, "Analyzed", analysis); return analysis;
    }
    @DeleteMapping("/job-descriptions/{analysisId}") public void deleteJobDescription(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Pattern(regexp="[0-9a-fA-F-]{36}") String analysisId) {
        if (!tools.deleteRecord(user.id(), "JOB_DESCRIPTION", analysisId)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job description analysis was not found.");
    }
    @PostMapping("/job-descriptions/{analysisId}/roadmap") public Map<String, Object> addJobGapsToRoadmap(@AuthenticationPrincipal CurrentUser user,
            @PathVariable @Pattern(regexp="[0-9a-fA-F-]{36}") String analysisId) {
        Map<String, Object> analysis = tools.record(user.id(), "JOB_DESCRIPTION", analysisId).map(r -> object(r.get("data")))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Job description analysis was not found."));
        Map<String, Object> roadmap = tools.record(user.id(), "ROADMAP", "active").map(r -> object(r.get("data")))
                .orElseGet(() -> generateRoadmap(user));
        Map<String, Object> phase = mapList(roadmap.get("phases")).stream().filter(item -> "job-preparation".equals(item.get("id"))).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Generate a target-role roadmap before adding job priorities."));
        List<Map<String, Object>> weeks = new ArrayList<>(mapList(phase.get("weeks")));
        int weekNumber = weeks.stream().mapToInt(item -> ((Number) item.getOrDefault("week", 0)).intValue()).max().orElse(0) + 1;
        List<Map<String, Object>> tasks = new ArrayList<>();
        java.util.Set<String> existingTaskIds = new java.util.HashSet<>();
        for (Map<String, Object> existingWeek : weeks) for (Map<String, Object> existingTask : mapList(existingWeek.get("tasks"))) existingTaskIds.add(String.valueOf(existingTask.get("id")));
        for (Map<String, Object> suggested : mapList(analysis.get("recommendedLearningTasks"))) {
            String title = String.valueOf(suggested.getOrDefault("title", "Review a missing job skill"));
            String skillKey = title.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
            String taskId = "job-description-" + analysisId.substring(0, 8) + "-" + skillKey;
            if (existingTaskIds.contains(taskId)) continue;
            tasks.add(new LinkedHashMap<>(Map.of("id", taskId,
                    "title", title, "detail", suggested.getOrDefault("detail", "Build verifiable evidence for this job requirement."),
                    "estimatedHours", 2, "resources", List.of(), "complete", false)));
        }
        if (tasks.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT, "This job description did not produce any learning tasks.");
        if (tasks.isEmpty()) return roadmap;
        weeks.add(new LinkedHashMap<>(Map.of("week", weekNumber, "title", "Job-specific priorities", "tasks", tasks)));
        phase.put("weeks", weeks); phase.put("durationWeeks", ((Number) phase.getOrDefault("durationWeeks", 0)).intValue() + 1);
        assistant.decorateRoadmap(roadmap); roadmap.put("updatedAt", java.time.Instant.now().toString());
        tools.saveRecord(user.id(), "ROADMAP", "active", "Active", roadmap); return roadmap;
    }

    @GetMapping("/applications") public List<Map<String, Object>> applications(@AuthenticationPrincipal CurrentUser user,
            @RequestParam(required=false) String status, @RequestParam(required=false) @Size(max=100) String search) {
        if (status != null && !List.of("Saved", "Applied", "Interview Scheduled", "Offer", "Rejected").contains(status)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown application status.");
        return tools.applications(user.id(), status, search);
    }
    @GetMapping("/applications/summary") public Map<String, Object> applicationSummary(@AuthenticationPrincipal CurrentUser user) {
        List<Map<String, Object>> values = tools.applications(user.id(), null, null); LocalDate today = LocalDate.now();
        long active = values.stream().filter(v -> List.of("Saved", "Applied", "Interview Scheduled").contains(v.get("status"))).count();
        long interviews = values.stream().filter(v -> "Interview Scheduled".equals(v.get("status"))).count(); long offers = values.stream().filter(v -> "Offer".equals(v.get("status"))).count();
        long due = values.stream().filter(v -> v.get("followUpDate") != null).filter(v -> !LocalDate.parse(String.valueOf(v.get("followUpDate"))).isAfter(today.plusDays(7))).count();
        Map<String, Long> byStatus = new LinkedHashMap<>(); for (String item : List.of("Saved", "Applied", "Interview Scheduled", "Offer", "Rejected")) byStatus.put(item, values.stream().filter(v -> item.equals(v.get("status"))).count());
        return Map.of("total", values.size(), "active", active, "interviewsScheduled", interviews, "offers", offers, "followUpsDue", due, "byStatus", byStatus);
    }
    @PostMapping("/applications") public Map<String, Object> createApplication(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody ApplicationRequest body) {
        return tools.application(user.id(), tools.createApplication(user.id(), applicationMap(body))).orElseThrow();
    }
    @PutMapping("/applications/{id}") public Map<String, Object> updateApplication(@AuthenticationPrincipal CurrentUser user, @PathVariable long id, @Valid @RequestBody ApplicationRequest body) {
        if (id <= 0 || !tools.updateApplication(user.id(), id, applicationMap(body))) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job application was not found.");
        return tools.application(user.id(), id).orElseThrow();
    }
    @DeleteMapping("/applications/{id}") public void deleteApplication(@AuthenticationPrincipal CurrentUser user, @PathVariable long id) {
        if (id <= 0 || !tools.deleteApplication(user.id(), id)) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Job application was not found.");
    }
    private static Map<String, Object> applicationMap(ApplicationRequest body) {
        Map<String, Object> value = new LinkedHashMap<>(); value.put("company", body.company().trim()); value.put("role", body.role().trim());
        value.put("jobLink", body.jobLink()); value.put("salary", body.salary()); value.put("location", body.location()); value.put("resumeVersion", body.resumeVersion());
        value.put("applicationDate", body.applicationDate() == null ? null : body.applicationDate().toString()); value.put("status", body.status()); value.put("notes", body.notes());
        value.put("followUpDate", body.followUpDate() == null ? null : body.followUpDate().toString()); return value;
    }

    private Map<String, Object> mentorContext(long userId) {
        Map<String, Object> context = new LinkedHashMap<>(); Map<String, Object> profile = intelligence.profile(userId);
        var target = intelligence.targetRole(userId); context.put("targetRole", target.map(t -> t.get("title")).orElse("not selected"));
        context.put("profileCompleteness", profile.getOrDefault("completeness", 0));
        context.put("weeklyLearningHours", profile.getOrDefault("weeklyLearningHours", "not set"));
        context.put("careerGoal", profile.getOrDefault("longTermGoal", "not set"));
        context.put("interests", profile.getOrDefault("interests", List.of()));
        Map<String, Object> latestResume = intelligence.latestResume(userId).orElse(Map.of());
        if (latestResume.get("report") instanceof Map<?, ?> report) {
            context.put("resumeScore", report.get("atsScore")); Object missing = report.get("missingKeywords"); context.put("resumeMissingKeywords", missing instanceof List<?> ? missing : List.of());
        }
        Map<String, Object> gaps = careerIntelligence.skillGapReport(userId); context.put("prioritySkillGaps", mapList(gaps.get("skills")).stream()
                .filter(skill -> !"Nice to have".equals(skill.get("priority"))).limit(6).map(skill -> skill.get("name")).toList());
        tools.record(userId, "ROADMAP", "active").ifPresent(record -> {
            Map<String, Object> roadmap = object(record.get("data")); context.put("roadmapProgress", roadmap.getOrDefault("completionPercent", 0));
            context.put("nextTask", nextRoadmapTask(roadmap));
        });
        context.put("projectStatus", tools.records(userId, "PROJECT", 20).stream().map(r -> {
            Map<String, Object> project = object(r.get("data")); return Map.of("title", project.getOrDefault("title", "Project"), "status", r.getOrDefault("status", "Planned"));
        }).toList());
        context.put("recentActivity", Map.of("resumeAnalyses", intelligence.recentResumes(userId).size(), "interviewSessions", tools.records(userId, "INTERVIEW", 10).size(), "jobApplications", tools.applications(userId, null, null).size()));
        accounts.latestAssessment(userId).ifPresent(value -> context.put("assessmentAvailable", true));
        return context;
    }
    private static Object nextRoadmapTask(Map<String, Object> roadmap) {
        for (Map<String, Object> phase : mapList(roadmap.get("phases"))) if ("active".equals(phase.get("state")))
            for (Map<String, Object> week : mapList(phase.get("weeks"))) for (Map<String, Object> task : mapList(week.get("tasks"))) if (!Boolean.TRUE.equals(task.get("complete"))) return task.get("title");
        return null;
    }

    private void completePortfolioRoadmapTask(long userId) {
        tools.record(userId, "ROADMAP", "active").ifPresent(record -> {
            Map<String, Object> roadmap = object(record.get("data"));
            for (Map<String, Object> phase : mapList(roadmap.get("phases"))) if ("portfolio".equals(phase.get("id"))) {
                for (Map<String, Object> week : mapList(phase.get("weeks"))) for (Map<String, Object> task : mapList(week.get("tasks")))
                    if ("portfolio-publish".equals(task.get("id"))) task.put("complete", true);
            }
            assistant.decorateRoadmap(roadmap); tools.saveRecord(userId, "ROADMAP", "active", "Active", roadmap);
        });
    }
    private static void preserveRoadmapProgress(Map<String, Object> old, Map<String, Object> next) {
        Map<String, Boolean> completed = new HashMap<>();
        for (Map<String, Object> phase : mapList(old.get("phases"))) {
            Map<String, Object> milestone = object(phase.get("milestone")); if (milestone.get("id") != null) completed.put(String.valueOf(milestone.get("id")), Boolean.TRUE.equals(milestone.get("complete")));
            for (Map<String, Object> week : mapList(phase.get("weeks"))) for (Map<String, Object> task : mapList(week.get("tasks"))) if (task.get("id") != null) completed.put(String.valueOf(task.get("id")), Boolean.TRUE.equals(task.get("complete")));
        }
        for (Map<String, Object> phase : mapList(next.get("phases"))) {
            Map<String, Object> milestone = object(phase.get("milestone")); if (completed.containsKey(String.valueOf(milestone.get("id")))) milestone.put("complete", completed.get(String.valueOf(milestone.get("id"))));
            for (Map<String, Object> week : mapList(phase.get("weeks"))) for (Map<String, Object> task : mapList(week.get("tasks")))
                if (completed.containsKey(String.valueOf(task.get("id")))) task.put("complete", completed.get(String.valueOf(task.get("id"))));
        }
    }

    public record RoadmapProgressRequest(@NotBlank @Size(max=80) String phaseId, @NotBlank @Size(max=120) String itemId,
            @NotBlank @Pattern(regexp="task|milestone") String itemType, boolean complete) { }
    public record SaveProjectRequest(@NotBlank @Pattern(regexp="[a-z0-9-]{3,80}") String projectKey) { }
    public record ProjectStatusRequest(@NotBlank @Pattern(regexp="Planned|In Progress|Completed") String status) { }
    public record MentorMessageRequest(@NotBlank @Size(max=3000) String message, @Pattern(regexp="[0-9a-fA-F-]{36}") String conversationId) { }
    public record InterviewStartRequest(@Pattern(regexp="Technical|HR|Behavioral|Role-specific") String type,
            @Pattern(regexp="Beginner|Intermediate|Advanced") String difficulty, @Size(max=180) String role) {
        public InterviewStartRequest { if (type == null) type = "Role-specific"; if (difficulty == null) difficulty = "Intermediate"; }
    }
    public record InterviewAnswerRequest(@NotBlank @Pattern(regexp="q[1-5]") String questionId, @NotBlank @Size(max=5000) String answer) { }
    public record JobDescriptionRequest(@NotBlank @Size(min=80, max=12000) String description, @Size(max=160) String company, @Size(max=180) String jobTitle) { }
    public record ApplicationRequest(@NotBlank @Size(max=160) String company, @NotBlank @Size(max=180) String role,
            @Pattern(regexp="^(https?://[^\\s]{1,1180})?$") String jobLink, @Size(max=120) String salary, @Size(max=160) String location, @Size(max=160) String resumeVersion,
            LocalDate applicationDate, @Pattern(regexp="Saved|Applied|Interview Scheduled|Offer|Rejected") String status,
            @Size(max=4000) String notes, LocalDate followUpDate) {
        public ApplicationRequest { if (status == null || status.isBlank()) status = "Saved"; }
    }

    private static Map<String, Object> object(Object value) { return value instanceof Map<?, ?> m ? (Map<String, Object>) m : new LinkedHashMap<>(); }
    private static List<Map<String, Object>> mapList(Object value) { return value instanceof List<?> list ? list.stream().filter(Map.class::isInstance).map(x -> (Map<String, Object>) x).toList() : List.of(); }
}
