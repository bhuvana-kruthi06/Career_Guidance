package edu.careerguidance.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.PreparedStatement;
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
public class CareerIntelligenceRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public CareerIntelligenceRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public Map<String, Object> saveResume(long userId, String roleCode, Map<String, Object> extracted, Map<String, Object> report) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO resume_analyses(user_id,target_role_code,extracted_json,report_json) VALUES(?,?,?,?)", new String[] {"id"});
            statement.setLong(1, userId); statement.setString(2, roleCode); statement.setString(3, write(extracted)); statement.setString(4, write(report));
            return statement;
        }, key);
        return resume(userId, key.getKey().longValue()).orElseThrow(() -> new IllegalStateException("Resume analysis was not saved."));
    }
    public List<Map<String, Object>> recentResumes(long userId) {
        return jdbc.query("SELECT id,target_role_code,report_json,created_at FROM resume_analyses WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 20",
                (rs, n) -> Map.of("id", rs.getLong("id"), "targetRoleCode", rs.getString("target_role_code") == null ? "" : rs.getString("target_role_code"),
                        "atsScore", readObject(rs.getString("report_json")).getOrDefault("atsScore", 0),
                        "targetRoleTitle", readObject(rs.getString("report_json")).getOrDefault("targetRoleTitle", "General resume review"),
                        "createdAt", rs.getTimestamp("created_at").toInstant().toString()), userId);
    }
    public Optional<Map<String, Object>> latestResume(long userId) {
        return jdbc.query("SELECT id,target_role_code,extracted_json,report_json,created_at FROM resume_analyses WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 1",
                (rs, n) -> resumeRow(rs.getLong("id"), rs.getString("target_role_code"), rs.getString("extracted_json"), rs.getString("report_json"), rs.getTimestamp("created_at").toInstant().toString()), userId)
                .stream().findFirst();
    }
    public Optional<Map<String, Object>> resume(long userId, long id) {
        return jdbc.query("SELECT id,target_role_code,extracted_json,report_json,created_at FROM resume_analyses WHERE user_id=? AND id=?",
                (rs, n) -> resumeRow(rs.getLong("id"), rs.getString("target_role_code"), rs.getString("extracted_json"), rs.getString("report_json"), rs.getTimestamp("created_at").toInstant().toString()), userId, id)
                .stream().findFirst();
    }
    public Map<String, Object> profile(long userId) {
        return jdbc.query("SELECT profile_json FROM student_profiles WHERE user_id=?", (rs, n) -> readObject(rs.getString(1)), userId)
                .stream().findFirst().orElseGet(java.util.HashMap::new);
    }
    public Optional<Map<String, Object>> targetRole(long userId) {
        return jdbc.query("SELECT target_role_code,target_role_title FROM student_profiles WHERE user_id=? AND target_role_code IS NOT NULL",
                (rs, n) -> Map.<String, Object>of("code", rs.getString(1), "title", rs.getString(2)), userId).stream().findFirst();
    }
    public Map<String, String> skillProgress(long userId, String roleCode) {
        return jdbc.query("SELECT skill_name,status FROM skill_gap_progress WHERE user_id=? AND role_code=?",
                rs -> { var values = new java.util.HashMap<String, String>(); while (rs.next()) values.put(rs.getString(1).toLowerCase(java.util.Locale.ROOT), rs.getString(2)); return values; }, userId, roleCode);
    }
    @Transactional
    public void updateSkillProgress(long userId, String roleCode, String skillName, String status) {
        jdbc.update("DELETE FROM skill_gap_progress WHERE user_id=? AND role_code=? AND skill_name=?", userId, roleCode, skillName);
        jdbc.update("INSERT INTO skill_gap_progress(user_id,role_code,skill_name,status) VALUES(?,?,?,?)", userId, roleCode, skillName, status);
    }

    private Map<String, Object> resumeRow(long id, String roleCode, String extracted, String report, String createdAt) {
        return Map.of("id", id, "targetRoleCode", roleCode == null ? "" : roleCode,
                "extracted", readObject(extracted), "report", readObject(report), "createdAt", createdAt);
    }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("Resume analysis data could not be stored."); } }
    private Map<String, Object> readObject(String value) { try { return json.readValue(value, new TypeReference<>() {}); } catch (Exception e) { throw new IllegalStateException("Stored career analysis could not be read."); } }
}
