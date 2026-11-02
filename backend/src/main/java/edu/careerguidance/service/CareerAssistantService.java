package edu.careerguidance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Shared role-aware planning, project, mentoring, interview and job-description intelligence. */
@Service
public class CareerAssistantService {
    private final CareerAIProvider provider;
    private final ResourceCatalogService resources;
    private final List<Map<String, Object>> projectCatalog;

    public CareerAssistantService(CareerAIProvider provider, ResourceCatalogService resources, ObjectMapper mapper,
            @Value("${career-guidance.project-root:.}") String root) {
        this.provider = provider; this.resources = resources;
        List<Map<String, Object>> projects = List.of(); Path candidate = Path.of(root).toAbsolutePath().normalize();
        for (int depth = 0; depth < 4; depth++) {
            Path file = candidate.resolve("data/projects/project_catalog.json");
            if (Files.isRegularFile(file)) {
                try { projects = mapper.readValue(file.toFile(), new TypeReference<>() {}); }
                catch (IOException e) { throw new IllegalStateException("Project recommendations could not be read."); }
                break;
            }
            if (candidate.getParent() == null) break;
            candidate = candidate.getParent();
        }
        projectCatalog = projects;
    }

    public Map<String, Object> generateRoadmap(Map<String, Object> profile, Map<String, Object> target,
            Map<String, Object> skillGaps, Map<String, Object> latestResume, Map<String, Object> assessment) {
        String role = String.valueOf(target.getOrDefault("title", "your selected career role"));
        String code = String.valueOf(target.getOrDefault("code", ""));
        int weeklyHours = positiveInt(profile.get("weeklyLearningHours"), 6, 2, 40);
        String goal = string(profile.get("longTermGoal"), "Build role-ready skills and portfolio evidence");
        List<Map<String, Object>> skills = mapList(skillGaps.get("skills"));
        List<String> critical = skills.stream().filter(s -> "Critical".equals(s.get("priority"))).map(s -> string(s.get("name"), "")).filter(s -> !s.isBlank()).limit(8).toList();
        List<String> nextSkills = skills.stream().sorted(Comparator.comparingInt((Map<String, Object> s) -> priorityRank(string(s.get("priority"), "")))
                .thenComparing((a, b) -> Double.compare(number(b.get("importance")), number(a.get("importance")))))
                .map(s -> string(s.get("name"), "")).filter(s -> !s.isBlank()).distinct().limit(12).toList();
        List<Map<String, Object>> phases = new ArrayList<>();
        phases.add(phase("foundation", "Foundation", "Set up a consistent learning routine and establish your current baseline.", List.of(
                task("profile-baseline", "Review your baseline", "Check your profile, assessment and current skill ratings for " + role + ".", 2, List.of()),
                task("weekly-rhythm", "Plan your weekly learning blocks", "Reserve about " + weeklyHours + " hours each week and choose a recurring review time.", 1, List.of())), "Baseline and weekly plan are ready.", weeklyHours));
        phases.add(phase("core-skills", "Core skills", "Build the high-priority skills employers commonly expect for " + role + ".", List.of(
                task("core-practice-1", critical.isEmpty() ? "Strengthen role fundamentals" : "Start with " + critical.getFirst(), "Complete a focused lesson, then practise with a small exercise.", 4, resourceFor(critical.isEmpty() ? "Programming" : critical.getFirst())),
                task("core-practice-2", critical.size() > 1 ? "Practise " + critical.get(1) : "Consolidate a second core skill", "Use an applied exercise and write down what you can now do independently.", 4, resourceFor(critical.size() > 1 ? critical.get(1) : "Critical Thinking"))), "Complete two practice artifacts and update your skill ratings.", weeklyHours));
        phases.add(phase("advanced-skills", "Advanced skills", "Move from fundamentals to the skills that distinguish strong candidates.", List.of(
                task("advanced-practice-1", "Develop " + nth(nextSkills, 2, "a role-specific skill"), "Study a realistic use case and document trade-offs and decisions.", 5, resourceFor(nth(nextSkills, 2, ""))),
                task("advanced-practice-2", "Apply " + nth(nextSkills, 3, "your skills together"), "Combine two skills in a small, reviewable technical or analytical exercise.", 5, resourceFor(nth(nextSkills, 3, "")))), "Explain one advanced decision with evidence.", weeklyHours));
        phases.add(phase("portfolio", "Portfolio projects", "Turn learning into demonstrable work relevant to your target role.", List.of(
                task("portfolio-scope", "Choose a scoped portfolio project", "Pick a project that demonstrates " + nth(nextSkills, 0, "your strongest skills") + " and can be completed in manageable increments.", 2, List.of()),
                task("portfolio-publish", "Publish a clear project case study", "Document the problem, your contribution, decisions, outcome and next improvements.", 6, List.of())), "Publish one project with a README and evidence of your contribution.", weeklyHours));
        phases.add(phase("resume-linkedin", "Resume and LinkedIn", "Make your evidence easy for a recruiter to find and understand.", List.of(
                task("resume-tailor", "Tailor your resume to " + role, "Use accurate role keywords and quantify outcomes only where you can support them.", 2, List.of()),
                task("linkedin-refresh", "Refresh your professional profile", "Align your headline, skills and project links with your selected direction.", 2, List.of())), "Resume and profile point to the same target role and evidence.", weeklyHours));
        phases.add(phase("job-preparation", "Internship and job preparation", "Build a repeatable, focused application routine.", List.of(
                task("job-scan", "Review three relevant job descriptions", "Compare required skills and keep a short list of recurring evidence to add.", 2, List.of()),
                task("job-apply", "Prepare one tailored application", "Match your strongest project evidence to one real role and record the follow-up date.", 2, List.of())), "One tailored application is prepared and tracked.", weeklyHours));
        phases.add(phase("interview-preparation", "Interview preparation", "Practise concise explanations of your work and role-specific reasoning.", List.of(
                task("interview-story", "Prepare two evidence-based stories", "Use situation, task, action and result; keep each story grounded in your real work.", 2, List.of()),
                task("interview-practice", "Complete a role-specific practice interview", "Practise aloud, review feedback, and choose one improvement for your next attempt.", 3, List.of())), "Complete a mock interview and note your next practice topic.", weeklyHours));
        Map<String, Object> roadmap = new LinkedHashMap<>(); roadmap.put("roleCode", code); roadmap.put("roleTitle", role); roadmap.put("weeklyHours", weeklyHours);
        roadmap.put("careerGoal", goal); roadmap.put("phases", phases); roadmap.put("createdBy", "role_based_fallback");
        Map<String, Object> ratedSkills = asMap(profile.get("skills"));
        List<String> currentSkills = ratedSkills.entrySet().stream().filter(entry -> entry.getValue() instanceof Number n && n.intValue() >= 3).map(Map.Entry::getKey).limit(15).toList();
        roadmap.put("inputs", Map.of("criticalSkillGaps", critical, "currentSkills", currentSkills, "resumeAtsScore", latestResume.getOrDefault("atsScore", 0),
                "assessmentAvailable", !assessment.isEmpty(), "profileCompleteness", profile.getOrDefault("completeness", 0)));
        return decorateRoadmap(roadmap);
    }

    public Map<String, Object> decorateRoadmap(Map<String, Object> roadmap) {
        List<Map<String, Object>> phases = mapList(roadmap.get("phases")); boolean previousComplete = true; int complete = 0, total = 0;
        for (Map<String, Object> phase : phases) {
            List<Map<String, Object>> weeks = mapList(phase.get("weeks")); List<Map<String, Object>> tasks = new ArrayList<>();
            for (Map<String, Object> week : weeks) tasks.addAll(mapList(week.get("tasks")));
            boolean tasksComplete = !tasks.isEmpty() && tasks.stream().allMatch(t -> Boolean.TRUE.equals(t.get("complete")));
            Map<String, Object> milestone = asMap(phase.get("milestone"));
            boolean phaseComplete = tasksComplete && Boolean.TRUE.equals(milestone.get("complete"));
            phase.put("complete", phaseComplete); phase.put("state", phaseComplete ? "completed" : previousComplete ? "active" : "locked");
            complete += (int) tasks.stream().filter(t -> Boolean.TRUE.equals(t.get("complete"))).count() + (Boolean.TRUE.equals(milestone.get("complete")) ? 1 : 0);
            total += tasks.size() + 1; previousComplete = phaseComplete;
        }
        roadmap.put("completionPercent", total == 0 ? 0 : (int) Math.round(complete * 100.0 / total));
        roadmap.put("completedItems", complete); roadmap.put("totalItems", total); return roadmap;
    }

    public List<Map<String, Object>> recommendProjects(String roleTitle, List<Map<String, Object>> skillGaps) {
        String title = roleTitle.toLowerCase(Locale.ROOT); String family = title.contains("data") || title.contains("analyst") || title.contains("business intelligence") ? "data"
                : title.contains("machine learning") || title.contains("ai ") || title.contains("artificial intelligence") ? "ml"
                : title.contains("front end") || title.contains("frontend") || title.contains("web designer") ? "frontend"
                : title.contains("back end") || title.contains("backend") || title.contains("database") ? "backend" : "fullstack";
        List<String> gapNames = skillGaps.stream().filter(s -> number(s.get("gap")) > 0).map(s -> string(s.get("name"), "")).filter(s -> !s.isBlank()).limit(4).toList();
        List<Map<String, Object>> selected = projectCatalog.stream().filter(p -> family.equals(p.get("roleFamily"))).limit(3).toList();
        if (selected.isEmpty()) selected = projectCatalog.stream().limit(3).toList();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> template : selected) {
            Map<String, Object> project = new LinkedHashMap<>(template); project.put("projectKey", template.get("id"));
            project.put("targetRole", roleTitle); project.put("gapSkillsToPractice", gapNames);
            result.add(project);
        }
        return result;
    }

    public Map<String, Object> mentorReply(String prompt, Map<String, Object> context) {
        String fallback = mentorFallback(prompt, context); String contextText = toCompactContext(context);
        java.util.Optional<String> generated = provider.complete(contextText, prompt);
        String response = generated.orElse(fallback);
        String lower = prompt.toLowerCase(Locale.ROOT);
        List<Map<String, String>> actions = new ArrayList<>();
        if (lower.contains("resume")) actions.add(Map.of("label", "Analyze resume", "route", "/resume"));
        if (lower.contains("roadmap") || lower.contains("learn") || lower.contains("next")) actions.add(Map.of("label", "Open roadmap", "route", "/roadmap"));
        if (lower.contains("project")) actions.add(Map.of("label", "Explore projects", "route", "/projects"));
        if (lower.contains("interview") || lower.contains("prepare")) actions.add(Map.of("label", "Start mock interview", "route", "/interview"));
        if (actions.isEmpty()) actions.add(Map.of("label", "Review skill gaps", "route", "/skill-gaps"));
        return Map.of("summary", response, "keyFindings", findings(context), "recommendedActions", actions,
                "relevantProfileData", compactProfile(context), "provider", generated.isPresent() ? provider.providerName() : "offline_fallback");
    }

    public List<Map<String, Object>> interviewQuestions(String role, String type, String difficulty, List<Map<String, Object>> gaps) {
        List<String> skills = gaps.stream().map(s -> string(s.get("name"), "")).filter(s -> !s.isBlank()).limit(5).toList();
        String skill = nth(skills, 0, "a role-relevant skill");
        List<String> prompts = switch (type) {
            case "Technical" -> List.of("How would you explain the core idea behind " + skill + " to a teammate?", "Describe how you would debug a failure in a " + role + " task.", "What trade-off would you consider when choosing an approach for " + skill + "?", "Walk through a technical decision you made and how you evaluated it.", "How would you validate that your solution works for edge cases?");
            case "HR" -> List.of("What interests you about working as a " + role + "?", "What kind of team helps you do your best work?", "Tell me about a strength you would bring to this role.", "What are you hoping to learn in your next position?", "What questions would help you decide whether this role is right for you?");
            case "Behavioral" -> List.of("Tell me about a time you worked through a difficult problem.", "Describe a time you received feedback and what you changed.", "Tell me about a time you collaborated across different viewpoints.", "Describe a mistake that changed how you approach your work.", "Give an example of how you managed competing priorities.");
            default -> List.of("How have you used " + skill + " in a project or learning exercise?", "What would you focus on during your first month as a " + role + "?", "Describe how you would approach a task that needs " + nth(skills, 1, "structured problem solving") + ".", "Which part of your portfolio best demonstrates your fit for this role?", "What would you like to improve next as a " + role + "?");
        };
        List<Map<String, Object>> result = new ArrayList<>(); for (int i = 0; i < prompts.size(); i++) result.add(Map.of("id", "q" + (i + 1), "prompt", prompts.get(i), "number", i + 1));
        return result;
    }

    public Map<String, Object> evaluateAnswer(String question, String answer, String role, String difficulty) {
        String lower = answer.toLowerCase(Locale.ROOT); int words = answer.trim().isEmpty() ? 0 : answer.trim().split("\\s+").length;
        int answerDetail = Math.min(100, 30 + Math.min(50, words)); int structure = Math.min(100, 35 + (lower.matches("(?s).*(situation|task|action|result|first|then|finally).*" ) ? 35 : 0) + Math.min(30, words / 3));
        int communication = Math.max(20, Math.min(100, 100 - Math.max(0, answer.length() - 900) / 30 - Math.max(0, (words / 25) - 4) * 5));
        List<String> missing = new ArrayList<>(); if (words < 35) missing.add("Add enough context for the interviewer to understand the challenge.");
        if (!lower.matches("(?s).*(result|outcome|learned|improved|reduced|delivered).*")) missing.add("State the outcome or what you learned.");
        if (!lower.matches("(?s).*(i |my |we ).*")) missing.add("Make your own contribution explicit.");
        List<Integer> scores = List.of(answerDetail, structure, communication); int score = scores.stream().mapToInt(Integer::intValue).sum() / scores.size();
        String feedback = "Local fallback for " + role + " at " + difficulty.toLowerCase(Locale.ROOT) + " level: this rubric checks answer detail and structure, but cannot verify factual or technical correctness. Check those claims against a reliable source.";
        Map<String, Object> result = new LinkedHashMap<>(); result.put("answerDetail", answerDetail); result.put("structure", structure); result.put("communication", communication);
        result.put("missingPoints", missing); result.put("feedback", feedback);
        result.put("improvedSampleAnswer", "Use your own real example: Situation and task → the action you personally took → the outcome or lesson. For \"" + question + "\", name the relevant decision and evidence without adding claims that are not yours.");
        provider.complete("Role: " + role + "; interview difficulty: " + difficulty + "; question: " + question,
                "Assess the answer's correctness to the extent you can, its structure, and communication. Point out inaccurate or unsupported claims; give concise actionable feedback and do not invent the candidate's experience: " + answer).ifPresent(ai -> result.put("aiFeedback", ai));
        result.put("score", score); return result;
    }

    public Map<String, Object> analyzeJobDescription(String description, Map<String, Object> profile,
            Map<String, Object> target, Map<String, Object> resume, Map<String, Object> skillGap) {
        String lower = description.toLowerCase(Locale.ROOT); List<String> userSkills = new ArrayList<>();
        if (profile.get("skills") instanceof Map<?, ?> map) map.keySet().stream().filter(String.class::isInstance).map(String.class::cast).forEach(userSkills::add);
        if (resume.get("extracted") instanceof Map<?, ?> extracted && extracted.get("skills") instanceof List<?> list) list.stream().filter(String.class::isInstance).map(String.class::cast).forEach(userSkills::add);
        Set<String> known = userSkills.stream().map(s -> s.toLowerCase(Locale.ROOT).trim()).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
        List<Map<String, Object>> gaps = mapList(skillGap.get("skills"));
        List<String> roleTerms = gaps.stream().filter(item -> number(item.get("importance")) >= 2.0).map(item -> string(item.get("name"), "")).filter(s -> !s.isBlank()).distinct().limit(30).toList();
        LinkedHashSet<String> mentioned = new LinkedHashSet<>();
        for (String term : roleTerms) if (lower.contains(term.toLowerCase(Locale.ROOT))) mentioned.add(term);
        for (String term : userSkills) if (term.length() > 2 && lower.contains(term.toLowerCase(Locale.ROOT))) mentioned.add(term);
        List<String> requirements = mentioned.isEmpty() ? roleTerms.stream().limit(15).toList() : mentioned.stream().limit(20).toList();
        List<String> matched = requirements.stream().filter(s -> known.contains(s.toLowerCase(Locale.ROOT))).toList();
        List<String> missing = requirements.stream().filter(s -> !known.contains(s.toLowerCase(Locale.ROOT))).limit(15).toList();
        int coverage = requirements.isEmpty() ? 0 : (int) Math.round(matched.size() * 100.0 / requirements.size());
        int resumeScore = resume.get("report") instanceof Map<?, ?> report && report.get("atsScore") instanceof Number n ? n.intValue() : 50;
        int match = Math.min(100, (int) Math.round(coverage * .8 + resumeScore * .2));
        List<String> keywords = missing.stream().limit(12).toList();
        List<String> suggestions = new ArrayList<>();
        if (!matched.isEmpty()) suggestions.add("Use real examples to show these matched skills: " + String.join(", ", matched.stream().limit(6).toList()) + ".");
        if (!missing.isEmpty()) suggestions.add("Prioritize the missing requirements through a project or learning task before claiming them.");
        if (resumeScore < 70) suggestions.add("Your saved resume review has a score of " + resumeScore + "; improve its section clarity and measurable evidence before applying.");
        String title = string(target.get("title"), "Job description match");
        return Map.of("matchPercent", match, "targetRoleTitle", title, "matchedSkills", matched, "missingSkills", missing,
                "missingKeywords", keywords, "resumeSuggestions", suggestions, "recommendedLearningTasks", missing.stream().limit(5).map(skill -> Map.of("title", "Build evidence for " + skill, "detail", "Study and practise this skill, then add verifiable evidence to your profile or portfolio.")).toList(),
                "method", "local_profile_and_role_keyword_comparison");
    }

    private Map<String, Object> phase(String id, String title, String description, List<Map<String, Object>> tasks, String milestoneText, int weeklyHours) {
        List<Map<String, Object>> weeks = new ArrayList<>(); int week = 1;
        for (Map<String, Object> task : tasks) {
            int span = Math.max(1, (int) Math.ceil(number(task.get("estimatedHours")) / weeklyHours)); task.put("durationWeeks", span);
            weeks.add(Map.of("week", week, "title", span == 1 ? "Week " + week : "Weeks " + week + "–" + (week + span - 1), "tasks", List.of(task))); week += span;
        }
        Map<String, Object> result = new LinkedHashMap<>(); result.put("id", id); result.put("title", title); result.put("description", description);
        result.put("durationWeeks", week - 1); result.put("weeks", weeks);
        result.put("milestone", Map.of("id", id + "-milestone", "title", milestoneText, "complete", false)); return result;
    }
    private Map<String, Object> task(String id, String title, String detail, int hours, List<Map<String, Object>> resources) {
        Map<String, Object> task = new LinkedHashMap<>(); task.put("id", id); task.put("title", title); task.put("detail", detail);
        task.put("estimatedHours", hours); task.put("resources", resources); task.put("complete", false); return task;
    }
    private List<Map<String, Object>> resourceFor(String skill) {
        if (skill == null || skill.isBlank()) return List.of();
        List<Map<String, Object>> found = resources.all().stream().filter(item -> skill.equalsIgnoreCase(String.valueOf(item.getOrDefault("skill", "")))).limit(2).toList();
        if (!found.isEmpty()) return found;
        return List.of(Map.of("title", "Explore learning resources for " + skill, "url", "https://www.open.edu/openlearn/", "provider", "OpenLearn", "skill", skill));
    }
    private String mentorFallback(String prompt, Map<String, Object> context) {
        String lower = prompt.toLowerCase(Locale.ROOT); String role = string(context.get("targetRole"), "your target role");
        if (lower.contains("resume")) return "Start with evidence from your latest resume review. Check its missing role keywords and improve one real project or experience bullet with a specific action and outcome. Do not add a skill or result you cannot support.";
        if (lower.contains("project")) return "Choose one small portfolio project that demonstrates a target-role skill, then publish the problem, your decisions, and verifiable results. The Projects area has role-matched ideas you can mark Planned or In Progress.";
        if (lower.contains("interview") || lower.contains("prepare")) return "Practise one question at a time for " + role + ". Use a real example, make your contribution clear, and finish with the result or lesson. Open Mock Interview to get structured feedback.";
        if (lower.contains("roadmap") || lower.contains("learn") || lower.contains("next")) {
            Object next = context.get("nextTask"); return next == null ? "Generate your personalized roadmap for " + role + " and start with its first unlocked task." : "Your next roadmap step is “" + next + "”. Keep it small enough to complete within your weekly learning time, then record what you learned.";
        }
        if (lower.contains("career") || lower.contains("path")) return "Your current target is " + role + ". Compare its skills with your profile and interests, then test your interest through a small project or a conversation with someone in the field.";
        return "Let’s make this practical for " + role + ". Review your highest-priority skill gaps, choose one achievable action this week, and keep the outcome in your roadmap so your plan stays grounded in progress.";
    }
    private List<String> findings(Map<String, Object> context) {
        List<String> values = new ArrayList<>(); if (context.containsKey("targetRole")) values.add("Target role: " + context.get("targetRole"));
        if (context.containsKey("resumeScore")) values.add("Latest resume review: " + context.get("resumeScore") + " ATS signal");
        if (context.get("nextTask") != null) values.add("Next roadmap task: " + context.get("nextTask"));
        return values;
    }
    private List<String> compactProfile(Map<String, Object> context) { return findings(context); }
    private String toCompactContext(Map<String, Object> context) { return context.toString().length() > 3000 ? context.toString().substring(0, 3000) : context.toString(); }
    private static List<Map<String, Object>> mapList(Object value) { return value instanceof List<?> list ? list.stream().filter(Map.class::isInstance).map(x -> (Map<String, Object>) x).toList() : List.of(); }
    private static Map<String, Object> asMap(Object value) { return value instanceof Map<?, ?> map ? (Map<String, Object>) map : new HashMap<>(); }
    private static String string(Object value, String fallback) { return value == null || String.valueOf(value).isBlank() ? fallback : String.valueOf(value); }
    private static int positiveInt(Object value, int fallback, int min, int max) {
        try { String text = String.valueOf(value).trim().replace('+', ' '); String first = text.split("[–—-]")[0].replaceAll("[^0-9]", "");
            return Math.min(max, Math.max(min, Integer.parseInt(first))); } catch (Exception e) { return fallback; }
    }
    private static double number(Object value) { return value instanceof Number n ? n.doubleValue() : 0; }
    private static int priorityRank(String value) { return switch (value) { case "Critical" -> 0; case "Important" -> 1; default -> 2; }; }
    private static String nth(List<String> values, int index, String fallback) { return values.size() > index ? values.get(index) : fallback; }
}
