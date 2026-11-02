package edu.careerguidance.service;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Deterministic resume parser/scorer. No resume text is sent to an external AI provider. */
@Service
public class ResumeAnalyzerService {
    private static final int MAX_PAGES = 15;
    private static final int MAX_EXTRACTED_CHARS = 100_000;
    private static final List<String> COMMON_SKILLS = List.of("Python", "Java", "JavaScript", "TypeScript", "C++", "C#", "HTML", "CSS", "React", "Angular", "Vue", "Node.js", "Spring Boot", "REST APIs", "SQL", "MySQL", "PostgreSQL", "MongoDB", "Redis", "AWS", "Azure", "Google Cloud", "Docker", "Kubernetes", "Linux", "Git", "Machine Learning", "Artificial Intelligence", "Data Analysis", "Data Visualization", "Tableau", "Power BI", "Excel", "Figma", "Communication", "Leadership", "Teamwork", "Project Management", "Critical Thinking", "Problem Solving", "Research", "Active Listening", "Writing", "Time Management", "Agile", "CI/CD", "Cybersecurity");
    private static final List<String> HEADINGS = List.of("summary", "profile", "skills", "technical skills", "experience", "work experience", "professional experience", "internships", "education", "projects", "certifications", "licenses");
    private static final Pattern EMAIL = Pattern.compile("(?i)[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}");
    private static final Pattern PHONE = Pattern.compile("(?<!\\w)(?:\\+?\\d[\\d ()().-]{7,}\\d)(?!\\w)");
    private static final Pattern VAGUE = Pattern.compile("(?i)\\b(responsible for|worked on|helped with|assisted with|various tasks|duties included|involved in)\\b");
    private static final Pattern METRIC = Pattern.compile("(?i)(\\d+\\s?%|\\$\\s?\\d|\\b\\d{2,}\\b|\\b(increased|reduced|saved|grew|improved)\\b)");

    private final CareerCatalogService careers;
    public ResumeAnalyzerService(CareerCatalogService careers) { this.careers = careers; }

    public Map<String, Object> analyze(byte[] pdf, String targetRoleCode) {
        String text;
        int pages;
        try (PDDocument document = Loader.loadPDF(pdf)) {
            if (document.isEncrypted()) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "This PDF is encrypted. Upload an unlocked copy.");
            pages = document.getNumberOfPages();
            if (pages < 1 || pages > MAX_PAGES) throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Resume PDFs must contain 1 to 15 pages.");
            PDFTextStripper stripper = new PDFTextStripper(); stripper.setSortByPosition(true);
            text = stripper.getText(document);
        } catch (ResponseStatusException e) { throw e; }
        catch (IOException | RuntimeException e) { throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "The PDF could not be read. Export a standard, unlocked PDF and try again."); }
        text = sanitize(text);
        if (text.length() > MAX_EXTRACTED_CHARS) text = text.substring(0, MAX_EXTRACTED_CHARS);
        if (text.replaceAll("\\s", "").length() < 80)
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "No selectable text was found. This may be a scanned PDF; use a text-based PDF or run OCR before uploading.");

        Map<String, Object> role = targetRoleCode == null || targetRoleCode.isBlank() ? null : careers.detail(targetRoleCode);
        Map<String, Object> extracted = extract(text, role);
        Map<String, Object> report = report(text, extracted, role, pages);
        return Map.of("extracted", extracted, "report", report);
    }

    @SuppressWarnings("unchecked") private Map<String, Object> extract(String text, Map<String, Object> role) {
        String email = first(EMAIL, text), phone = first(PHONE, text);
        String linkedIn = firstLink(text, "linkedin\\.com/[^\\s|,)]+"), github = firstLink(text, "github\\.com/[^\\s|,)]+");
        String name = text.lines().map(String::trim).filter(line -> !line.isBlank() && line.length() <= 80
                && !EMAIL.matcher(line).find() && !PHONE.matcher(line).find() && !line.toLowerCase(Locale.ROOT).contains("linkedin.com")
                && !line.toLowerCase(Locale.ROOT).contains("github.com") && !isHeading(line)).findFirst().orElse("");
        List<String> education = educationLines(text);
        List<String> projects = sectionLines(text, Set.of("projects", "selected projects", "academic projects"));
        List<String> internships = sectionLines(text, Set.of("internships", "internship experience"));
        List<String> experience = sectionLines(text, Set.of("experience", "work experience", "professional experience", "employment"));
        List<String> certifications = sectionLines(text, Set.of("certifications", "certificates", "licenses"));
        List<String> skillTerms = new ArrayList<>(COMMON_SKILLS);
        if (role != null && role.get("requirements") instanceof List<?> requirements) for (Object raw : requirements) {
            if (raw instanceof Map<?, ?> item && item.get("element_name") instanceof String value && !skillTerms.contains(value)) skillTerms.add(value);
        }
        LinkedHashSet<String> foundSkills = new LinkedHashSet<>();
        for (String skill : skillTerms) if (containsPhrase(text, skill)) foundSkills.add(skill);
        Map<String, Object> extracted = new java.util.LinkedHashMap<>();
        extracted.put("name", name); extracted.put("email", email); extracted.put("phone", phone);
        extracted.put("linkedIn", linkedIn); extracted.put("github", github); extracted.put("education", education);
        extracted.put("skills", new ArrayList<>(foundSkills)); extracted.put("projects", projects);
        extracted.put("internships", internships); extracted.put("experience", experience);
        extracted.put("certifications", certifications); extracted.put("pages", Math.max(1, countPages(text)));
        return extracted;
    }

    private Map<String, Object> report(String text, Map<String, Object> extracted, Map<String, Object> role, int pages) {
        @SuppressWarnings("unchecked") List<String> skills = (List<String>) extracted.get("skills");
        List<Map<String, Object>> requirements = role != null && role.get("requirements") instanceof List<?> list
                ? (List<Map<String, Object>>) (List<?>) list : List.of();
        List<String> required = requirements.stream().map(item -> String.valueOf(item.get("element_name"))).distinct().toList();
        List<String> missing = role == null ? List.of() : required.stream().filter(skill -> !containsPhrase(text, skill)).limit(30).toList();
        int contactScore = (present(extracted.get("name")) ? 35 : 0) + (present(extracted.get("email")) ? 35 : 0)
                + (present(extracted.get("phone")) ? 15 : 0) + (present(extracted.get("linkedIn")) || present(extracted.get("github")) ? 15 : 0);
        int skillsScore = skills.isEmpty() ? 20 : Math.min(100, 35 + skills.size() * 6);
        int experienceScore = sectionScore(text, Set.of("experience", "work experience", "professional experience", "internships"), true);
        int educationScore = sectionScore(text, Set.of("education", "academic background"), false);
        int projectScore = sectionScore(text, Set.of("projects", "selected projects"), true);
        int keywordScore = role == null ? 65 : required.isEmpty() ? 65 : (int) Math.round((required.size() - missing.size()) * 100.0 / required.size());
        List<String> weakBullets = text.lines().map(String::trim).filter(line -> (line.startsWith("•") || line.startsWith("-") || line.startsWith("*") || line.startsWith("–"))
                && VAGUE.matcher(line).find()).map(line -> line.length() > 260 ? line.substring(0, 260) + "…" : line).limit(12).toList();
        int impactScore = text.lines().filter(line -> (line.startsWith("•") || line.startsWith("-") || line.startsWith("*")) && METRIC.matcher(line).find()).count() > 0 ? 100 : 35;
        int ats = (int) Math.round(contactScore * .20 + skillsScore * .20 + experienceScore * .20 + educationScore * .15 + projectScore * .10 + keywordScore * .10 + impactScore * .05);
        List<Map<String, Object>> sections = List.of(section("Contact details", contactScore), section("Skills", skillsScore),
                section("Experience", experienceScore), section("Education", educationScore), section("Projects", projectScore),
                section(role == null ? "Role keywords" : "Target role keywords", keywordScore), section("Impact and metrics", impactScore));
        List<Map<String, String>> suggestions = new ArrayList<>();
        if (!present(extracted.get("email")) || !present(extracted.get("phone"))) suggestions.add(suggestion("Important", "Complete your contact details", "Include a professional email and phone number near the top of the document."));
        if (skillsScore < 60) suggestions.add(suggestion("Important", "Make skills easy to scan", "Add a concise skills section using accurate terms that you can support with examples."));
        if (!missing.isEmpty()) suggestions.add(suggestion("Critical", "Add relevant target-role language", "Where it truthfully reflects your experience, use these role keywords: " + String.join(", ", missing.stream().limit(8).toList()) + "."));
        if (weakBullets.size() > 0) suggestions.add(suggestion("Important", "Rewrite vague experience bullets", "Start with a specific action, explain the work, and add a measurable outcome where you can verify one."));
        if (impactScore < 60) suggestions.add(suggestion("Nice to have", "Show evidence of impact", "Add real counts, percentages, time saved, or scale to relevant accomplishment bullets."));
        if (pages > 2) suggestions.add(suggestion("Nice to have", "Review document length", "This resume is longer than two pages. Keep only relevant detail for your experience level."));
        if (suggestions.isEmpty()) suggestions.add(suggestion("Nice to have", "Keep the evidence specific", "Tailor examples to the target role and verify every claim before sending."));
        List<String> formatting = new ArrayList<>();
        if (pages > 2) formatting.add("The document spans " + pages + " pages; consider a concise two-page version.");
        if (text.lines().anyMatch(line -> line.length() > 180)) formatting.add("Some extracted lines are very long; check columns, wrapping, and reading order in the PDF.");
        if (!text.toLowerCase(Locale.ROOT).contains("linkedin.com") && !text.toLowerCase(Locale.ROOT).contains("github.com")) formatting.add("Add a professional LinkedIn or portfolio link if relevant to your field.");
        return Map.of("atsScore", ats, "sectionScores", sections, "missingKeywords", missing,
                "weakBullets", weakBullets, "formattingSuggestions", formatting, "suggestions", suggestions,
                "targetRoleTitle", role == null ? "General resume review" : role.getOrDefault("occupation_title", "Selected role"),
                "targetRoleFeedback", role == null ? "Select a target role to receive occupation-specific keyword feedback." :
                        (missing.isEmpty() ? "The resume text includes all of the highest-priority role skills found in the local O*NET profile." : "This feedback compares resume text with O*NET skill requirements; add missing terms only when they truthfully reflect your experience."),
                "scoreNote", "Heuristic completeness and keyword signal, not a guarantee of passing any employer’s ATS.");
    }

    private static List<String> educationLines(String text) {
        return text.lines().map(String::trim).filter(line -> line.length() <= 240 && Pattern.compile("(?i)\\b(bachelor|master|doctor|diploma|degree|university|college|school|b\\.?sc|m\\.?sc|b\\.?tech|m\\.?tech|ph\\.?d)\\b").matcher(line).find()).distinct().limit(10).toList();
    }
    private static List<String> sectionLines(String text, Set<String> wanted) {
        List<String> lines = text.lines().map(String::trim).toList(); List<String> result = new ArrayList<>(); boolean active = false;
        for (String line : lines) {
            String normalized = normalizeHeading(line);
            if (isHeading(line)) { active = wanted.contains(normalized); continue; }
            if (active && !line.isBlank()) result.add(line.length() > 350 ? line.substring(0, 350) + "…" : line);
        }
        return result.stream().distinct().limit(30).toList();
    }
    private static int sectionScore(String text, Set<String> headings, boolean requireContent) {
        List<String> found = sectionLines(text, headings);
        boolean heading = text.lines().map(ResumeAnalyzerService::normalizeHeading).anyMatch(headings::contains);
        if (heading && (!requireContent || !found.isEmpty())) return 100;
        if (!found.isEmpty()) return 65;
        String lower = text.toLowerCase(Locale.ROOT);
        return headings.stream().anyMatch(lower::contains) ? 50 : 20;
    }
    private static boolean isHeading(String line) { return HEADINGS.contains(normalizeHeading(line)); }
    private static String normalizeHeading(String line) { return line.replaceAll("[:|•*]", " ").trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " "); }
    private static boolean containsPhrase(String text, String phrase) {
        return Pattern.compile("(?i)(?<![\\p{Alnum}])" + Pattern.quote(phrase) + "(?![\\p{Alnum}])").matcher(text).find();
    }
    private static String first(Pattern pattern, String text) { var match = pattern.matcher(text); return match.find() ? match.group().trim() : ""; }
    private static String firstLink(String text, String expression) { var match = Pattern.compile("(?i)" + expression).matcher(text); return match.find() ? "https://" + match.group().replaceFirst("(?i)^https?://", "") : ""; }
    private static boolean present(Object value) { return value instanceof String text && !text.isBlank(); }
    private static Map<String, Object> section(String label, int score) { return Map.of("label", label, "score", Math.max(0, Math.min(score, 100))); }
    private static Map<String, String> suggestion(String severity, String title, String detail) { return Map.of("severity", severity, "title", title, "detail", detail); }
    private static String sanitize(String text) { return text == null ? "" : text.replaceAll("[\\p{Cc}&&[^\\r\\n\\t]]", "").replace('\u0000', ' ').trim(); }
    private static int countPages(String text) { return Math.max(1, (int) text.chars().filter(ch -> ch == '\f').count() + 1); }
}
