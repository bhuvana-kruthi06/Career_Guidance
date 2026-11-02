package edu.careerguidance.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CareerCatalogService {
    private static final Set<String> RIASEC = Set.of("R", "I", "A", "S", "E", "C");
    private final Map<String, Map<String, String>> careers = new HashMap<>();
    private final Map<String, List<Map<String, String>>> requirements = new HashMap<>();

    public CareerCatalogService(@Value("${career-guidance.project-root:.}") String root) {
        Path candidate = Path.of(root).toAbsolutePath().normalize();
        Path processed = candidate.resolve("data/processed");
        for (int depth = 0; depth < 4 && !Files.isRegularFile(processed.resolve("career_metadata.csv")); depth++) {
            if (candidate.getParent() == null) break;
            candidate = candidate.getParent(); processed = candidate.resolve("data/processed");
        }
        try {
            for (var row : readCsv(processed.resolve("career_metadata.csv"))) {
                if (row.get("onet_soc_code") != null) careers.put(row.get("onet_soc_code"), row);
            }
            for (var row : readCsv(processed.resolve("career_skill_map.csv"))) {
                if (row.get("onet_soc_code") != null) requirements.computeIfAbsent(row.get("onet_soc_code"), ignored -> new ArrayList<>()).add(row);
            }
        } catch (IOException ex) {
            careers.clear(); requirements.clear();
        }
        requirements.values().forEach(rows -> rows.sort(Comparator.comparingDouble((Map<String, String> row) -> number(row.get("importance_score"))).reversed()));
    }

    public boolean available() { return !careers.isEmpty(); }
    public SearchResults search(String query, String riasec, int limit, int offset) {
        if (query != null && query.length() > 80) throw new IllegalArgumentException("Search query must be at most 80 characters.");
        if (riasec != null && !riasec.isBlank() && !RIASEC.contains(riasec.toUpperCase(Locale.ROOT))) throw new IllegalArgumentException("RIASEC filter must be one of R, I, A, S, E, or C.");
        if (limit < 1 || limit > 50) throw new IllegalArgumentException("Limit must be between 1 and 50.");
        if (offset < 0 || offset > 10000) throw new IllegalArgumentException("Offset must be between 0 and 10000.");
        if (!available()) throw new CatalogUnavailableException();
        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<Map<String, String>> found = careers.values().stream()
                .filter(row -> needle.isEmpty() || row.getOrDefault("occupation_title", "").toLowerCase(Locale.ROOT).contains(needle)
                        || row.getOrDefault("description", "").toLowerCase(Locale.ROOT).contains(needle))
                .filter(row -> riasec == null || riasec.isBlank() || riasec.equalsIgnoreCase(row.get("riasec_top1"))
                        || riasec.equalsIgnoreCase(row.get("riasec_top2")) || riasec.equalsIgnoreCase(row.get("riasec_top3")))
                .sorted(Comparator.comparing(row -> row.getOrDefault("occupation_title", ""), String.CASE_INSENSITIVE_ORDER)).toList();
        int start = Math.min(offset, found.size());
        return new SearchResults(found.size(), found.stream().skip(start).limit(limit).map(this::publicCareer).toList(), limit, offset);
    }
    public Map<String, Object> detail(String code) {
        if (!available()) throw new CatalogUnavailableException();
        Map<String, String> career = careers.get(code);
        if (career == null) return null;
        List<Map<String, Object>> skills = requirements.getOrDefault(code, List.of()).stream().map(row -> {
            Map<String, Object> value = new HashMap<>();
            value.put("element_id", row.get("element_id")); value.put("element_name", row.get("element_name"));
            value.put("skill_category", row.get("skill_category")); value.put("importance", number(row.get("importance_score")));
            value.put("required_level", number(row.get("level_score"))); return value;
        }).toList();
        Map<String, Object> result = new HashMap<>(publicCareer(career)); result.put("requirements", skills); return result;
    }
    private Map<String, String> publicCareer(Map<String, String> row) {
        Map<String, String> career = new HashMap<>();
        for (String key : List.of("onet_soc_code", "occupation_title", "description", "job_zone", "riasec_top1", "riasec_top2", "riasec_top3"))
            if (row.get(key) != null && !row.get(key).equals("?")) career.put(key, row.get(key));
        return career;
    }
    private static double number(String value) { try { return Double.parseDouble(value); } catch (Exception e) { return 0; } }
    private static List<Map<String, String>> readCsv(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        if (lines.isEmpty()) return List.of();
        List<String> headers = parseLine(lines.getFirst()); List<Map<String, String>> rows = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).isBlank()) continue;
            List<String> values = parseLine(lines.get(i)); Map<String, String> row = new HashMap<>();
            for (int c = 0; c < Math.min(headers.size(), values.size()); c++) row.put(headers.get(c), values.get(c));
            rows.add(row);
        }
        return rows;
    }
    private static List<String> parseLine(String line) {
        List<String> values = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') { if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') { field.append('"'); i++; } else quoted = !quoted; }
            else if (ch == ',' && !quoted) { values.add(field.toString()); field.setLength(0); }
            else field.append(ch);
        }
        values.add(field.toString()); return values;
    }
    public record SearchResults(int total, List<Map<String, String>> results, int limit, int offset) { }
    public static final class CatalogUnavailableException extends RuntimeException { public CatalogUnavailableException() { super("Career catalog is unavailable; regenerate data/processed and restart the API."); } }
}
