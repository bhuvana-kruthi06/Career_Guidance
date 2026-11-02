package edu.careerguidance.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class AccountDataRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AccountDataRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public Optional<Map<String, Object>> profile(long userId) {
        return jdbc.query("SELECT profile_json,target_role_code,target_role_title,profile_completeness FROM student_profiles WHERE user_id=?",
                (rs, n) -> {
                    Map<String, Object> value = readObject(rs.getString("profile_json"));
                    value.put("targetRole", rs.getString("target_role_title") == null ? null
                            : Map.of("code", rs.getString("target_role_code"), "title", rs.getString("target_role_title")));
                    value.put("completeness", completeness(value)); return value;
                }, userId).stream().findFirst();
    }
    public void saveProfile(long userId, Map<String, Object> value) {
        String body = write(value);
        int completeness = completeness(value);
        if (jdbc.update("UPDATE student_profiles SET profile_json=?,profile_completeness=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", body, completeness, userId) == 0)
            jdbc.update("INSERT INTO student_profiles(user_id,profile_json,profile_completeness) VALUES(?,?,?)", userId, body, completeness);
    }
    public Map<String, Object> saveAssessment(long userId, Map<String, Object> assessment, List<Map<String, Object>> recommendations) {
        jdbc.update("INSERT INTO career_assessments(user_id,assessment_json,recommendations_json) VALUES(?,?,?)",
                userId, write(assessment), write(recommendations));
        return latestAssessment(userId).orElse(Map.of());
    }
    public Optional<Map<String, Object>> latestAssessment(long userId) {
        return jdbc.query("SELECT id,assessment_json,recommendations_json,created_at FROM career_assessments WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 1",
                (rs, n) -> Map.<String, Object>of("id", rs.getLong("id"), "assessment", readObject(rs.getString("assessment_json")),
                        "recommendations", readList(rs.getString("recommendations_json")), "createdAt", rs.getTimestamp("created_at").toInstant().toString()), userId)
                .stream().findFirst();
    }
    public void selectTargetRole(long userId, String code, String title) {
        if (jdbc.update("UPDATE student_profiles SET target_role_code=?,target_role_title=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=?", code, title, userId) == 0)
            jdbc.update("INSERT INTO student_profiles(user_id,profile_json,target_role_code,target_role_title) VALUES(?,?,?,?)", userId, "{}", code, title);
    }
    public List<Map<String, Object>> sessions(long userId) {
        return jdbc.query("SELECT id,skills_snapshot,interests_snapshot,results_json,created_at FROM match_sessions WHERE user_id=? ORDER BY created_at DESC,id DESC",
                (rs, n) -> Map.of("id", rs.getLong("id"), "skills", readObject(rs.getString("skills_snapshot")),
                        "interests", readList(rs.getString("interests_snapshot")), "results", readList(rs.getString("results_json")),
                        "createdAt", rs.getTimestamp("created_at").toInstant().toString()), userId);
    }
    public Optional<Map<String, Object>> session(long userId, long id) {
        return jdbc.query("SELECT id,skills_snapshot,interests_snapshot,results_json,created_at FROM match_sessions WHERE user_id=? AND id=?",
                (rs, n) -> Map.<String, Object>of("id", rs.getLong("id"), "skills", readObject(rs.getString("skills_snapshot")),
                        "interests", readList(rs.getString("interests_snapshot")), "results", readList(rs.getString("results_json")),
                        "createdAt", rs.getTimestamp("created_at").toInstant().toString()), userId, id).stream().findFirst();
    }
    @Transactional
    public long saveMatch(long userId, Map<String, ?> skills, List<String> interests, List<Map<String, Object>> results) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("INSERT INTO match_sessions(user_id,skills_snapshot,interests_snapshot,results_json) VALUES(?,?,?,?)", new String[] {"id"});
            statement.setLong(1, userId); statement.setString(2, write(skills)); statement.setString(3, write(interests)); statement.setString(4, write(results));
            return statement;
        }, key);
        long id = key.getKey().longValue();
        for (int i = 0; i < results.size(); i++) {
            Map<String, Object> result = results.get(i);
            Object code = result.get("onet_soc_code");
            jdbc.update("INSERT INTO match_results(session_id,result_rank,onet_soc_code,result_json) VALUES(?,?,?,?)", id, i + 1,
                    code == null ? "unknown" : code.toString(), write(result));
        }
        return id;
    }
    public boolean deleteSession(long userId, long id) { return jdbc.update("DELETE FROM match_sessions WHERE user_id=? AND id=?", userId, id) > 0; }
    public List<Map<String, Object>> saved(long userId) { return jdbc.query("SELECT career_json FROM saved_careers WHERE user_id=? ORDER BY created_at DESC", (rs, n) -> readObject(rs.getString(1)), userId); }
    public void saveCareer(long userId, String code, Map<String, Object> career) {
        jdbc.update("DELETE FROM saved_careers WHERE user_id=? AND onet_soc_code=?", userId, code);
        jdbc.update("INSERT INTO saved_careers(user_id,onet_soc_code,career_json) VALUES(?,?,?)", userId, code, write(career));
    }
    public boolean removeCareer(long userId, String code) { return jdbc.update("DELETE FROM saved_careers WHERE user_id=? AND onet_soc_code=?", userId, code) > 0; }
    public List<Map<String, Object>> roadmap(long userId) {
        return jdbc.query("SELECT item_key,item_json,is_complete,completed_at FROM roadmap_items WHERE user_id=? ORDER BY item_key", (rs, n) -> {
            var item = readObject(rs.getString("item_json")); item.put("key", rs.getString("item_key")); item.put("complete", rs.getBoolean("is_complete"));
            Timestamp done = rs.getTimestamp("completed_at"); item.put("completedAt", done == null ? null : done.toInstant().toString()); return item;
        }, userId);
    }
    @Transactional
    public void replaceRoadmap(long userId, List<Map<String, Object>> items) {
        jdbc.update("DELETE FROM roadmap_items WHERE user_id=?", userId);
        for (Map<String, Object> item : items) {
            String key = String.valueOf(item.get("key")); boolean complete = Boolean.TRUE.equals(item.get("complete"));
            Instant timestamp = item.get("completedAt") instanceof String value && !value.isBlank() ? Instant.parse(value) : (complete ? Instant.now() : null);
            jdbc.update("INSERT INTO roadmap_items(user_id,item_key,item_json,is_complete,completed_at) VALUES(?,?,?,?,?)",
                    userId, key, write(item), complete, timestamp == null ? null : Timestamp.from(timestamp));
        }
    }
    public boolean imported(long userId) {
        List<Boolean> values = jdbc.query("SELECT local_imported FROM student_profiles WHERE user_id=?", (rs, n) -> rs.getBoolean(1), userId);
        return !values.isEmpty() && values.getFirst();
    }
    public void markImported(long userId, Map<String, Object> profile) {
        saveProfile(userId, profile); jdbc.update("UPDATE student_profiles SET local_imported=TRUE WHERE user_id=?", userId);
    }
    @Transactional public void deleteAccount(long userId) { jdbc.update("DELETE FROM users WHERE id=?", userId); }

    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("Invalid data."); } }
    private Map<String, Object> readObject(String value) { try { return json.readValue(value, new TypeReference<>() {}); } catch (Exception e) { throw new IllegalStateException("Stored account data could not be read."); } }
    private List<Object> readList(String value) { try { return json.readValue(value, new TypeReference<>() {}); } catch (Exception e) { throw new IllegalStateException("Stored account data could not be read."); } }
    private int completeness(Map<String, Object> profile) {
        List<Boolean> checks = List.of(nonEmpty(profile.get("name")), nonEmpty(profile.get("education")),
                nonEmpty(profile.get("studyArea")), nonEmpty(profile.get("college")), nonEmpty(profile.get("graduationYear")),
                nonEmpty(profile.get("skills")), nonEmpty(profile.get("interests")), nonEmpty(profile.get("preferredRoles")),
                nonEmpty(profile.get("projects")), nonEmpty(profile.get("experience")), nonEmpty(profile.get("internships")),
                nonEmpty(profile.get("certifications")), nonEmpty(profile.get("targetCompanies")), nonEmpty(profile.get("preferredLocations")),
                nonEmpty(profile.get("weeklyLearningHours")), nonEmpty(profile.get("shortTermGoal")), nonEmpty(profile.get("longTermGoal")));
        return (int) Math.round(checks.stream().filter(Boolean::booleanValue).count() * 100.0 / checks.size());
    }
    private boolean nonEmpty(Object value) {
        if (value == null) return false;
        if (value instanceof String text) return !text.isBlank();
        if (value instanceof List<?> list) return !list.isEmpty();
        if (value instanceof Map<?, ?> map) return !map.isEmpty();
        return true;
    }
}
