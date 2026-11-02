package edu.careerguidance.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Date;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class CareerToolsRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public CareerToolsRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public Optional<Map<String, Object>> record(long userId, String type, String key) {
        return jdbc.query("SELECT record_key,status,record_json,created_at,updated_at FROM career_tool_records WHERE user_id=? AND record_type=? AND record_key=?",
                (rs, n) -> recordRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant().toString(), rs.getTimestamp(5).toInstant().toString()),
                userId, type, key).stream().findFirst();
    }
    public List<Map<String, Object>> records(long userId, String type, int limit) {
        int bounded = Math.max(1, Math.min(limit, 100));
        return jdbc.query("SELECT record_key,status,record_json,created_at,updated_at FROM career_tool_records WHERE user_id=? AND record_type=? ORDER BY updated_at DESC,id DESC LIMIT " + bounded,
                (rs, n) -> recordRow(rs.getString(1), rs.getString(2), rs.getString(3), rs.getTimestamp(4).toInstant().toString(), rs.getTimestamp(5).toInstant().toString()), userId, type);
    }
    public void saveRecord(long userId, String type, String key, String status, Map<String, Object> value) {
        jdbc.update("INSERT INTO career_tool_records(user_id,record_type,record_key,status,record_json) VALUES(?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE status=VALUES(status),record_json=VALUES(record_json),updated_at=CURRENT_TIMESTAMP",
                userId, type, key, status, write(value));
    }
    public boolean deleteRecord(long userId, String type, String key) {
        return jdbc.update("DELETE FROM career_tool_records WHERE user_id=? AND record_type=? AND record_key=?", userId, type, key) > 0;
    }

    public List<Map<String, Object>> applications(long userId, String status, String query) {
        String needle = query == null || query.isBlank() ? null : "%" + query.trim().replace("%", "\\%").replace("_", "\\_") + "%";
        return jdbc.query("SELECT id,company,role_title,job_link,salary,location,resume_version,application_date,status,notes,follow_up_date,created_at,updated_at " +
                "FROM job_applications WHERE user_id=? AND (? IS NULL OR status=?) AND (? IS NULL OR company LIKE ? OR role_title LIKE ?) " +
                "ORDER BY (follow_up_date IS NULL),follow_up_date DESC,updated_at DESC,id DESC",
                (rs, n) -> applicationRow(rs), userId, status, status, needle, needle, needle);
    }
    public Optional<Map<String, Object>> application(long userId, long id) {
        return jdbc.query("SELECT id,company,role_title,job_link,salary,location,resume_version,application_date,status,notes,follow_up_date,created_at,updated_at FROM job_applications WHERE user_id=? AND id=?",
                (rs, n) -> applicationRow(rs), userId, id).stream().findFirst();
    }
    public long createApplication(long userId, Map<String, Object> value) {
        jdbc.update("INSERT INTO job_applications(user_id,company,role_title,job_link,salary,location,resume_version,application_date,status,notes,follow_up_date) VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                userId, value.get("company"), value.get("role"), value.get("jobLink"), value.get("salary"), value.get("location"), value.get("resumeVersion"),
                date(value.get("applicationDate")), value.get("status"), value.get("notes"), date(value.get("followUpDate")));
        return jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
    public boolean updateApplication(long userId, long id, Map<String, Object> value) {
        return jdbc.update("UPDATE job_applications SET company=?,role_title=?,job_link=?,salary=?,location=?,resume_version=?,application_date=?,status=?,notes=?,follow_up_date=?,updated_at=CURRENT_TIMESTAMP WHERE user_id=? AND id=?",
                value.get("company"), value.get("role"), value.get("jobLink"), value.get("salary"), value.get("location"), value.get("resumeVersion"),
                date(value.get("applicationDate")), value.get("status"), value.get("notes"), date(value.get("followUpDate")), userId, id) > 0;
    }
    public boolean deleteApplication(long userId, long id) { return jdbc.update("DELETE FROM job_applications WHERE user_id=? AND id=?", userId, id) > 0; }

    private Map<String, Object> recordRow(String key, String status, String raw, String created, String updated) {
        Map<String, Object> result = new LinkedHashMap<>(); result.put("key", key); result.put("status", status);
        result.put("data", read(raw)); result.put("createdAt", created); result.put("updatedAt", updated); return result;
    }
    private Map<String, Object> applicationRow(java.sql.ResultSet rs) throws java.sql.SQLException {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", rs.getLong("id")); result.put("company", rs.getString("company")); result.put("role", rs.getString("role_title"));
        result.put("jobLink", rs.getString("job_link")); result.put("salary", rs.getString("salary")); result.put("location", rs.getString("location"));
        result.put("resumeVersion", rs.getString("resume_version")); result.put("applicationDate", stringDate(rs.getDate("application_date")));
        result.put("status", rs.getString("status")); result.put("notes", rs.getString("notes")); result.put("followUpDate", stringDate(rs.getDate("follow_up_date")));
        result.put("createdAt", rs.getTimestamp("created_at").toInstant().toString()); result.put("updatedAt", rs.getTimestamp("updated_at").toInstant().toString()); return result;
    }
    private static Date date(Object value) { return value instanceof String text && !text.isBlank() ? Date.valueOf(LocalDate.parse(text)) : null; }
    private static String stringDate(Date value) { return value == null ? null : value.toLocalDate().toString(); }
    private String write(Object value) { try { return json.writeValueAsString(value); } catch (Exception e) { throw new IllegalArgumentException("Could not store career tool data."); } }
    private Map<String, Object> read(String raw) { try { return json.readValue(raw, new TypeReference<>() {}); } catch (Exception e) { throw new IllegalStateException("Stored career tool data could not be read."); } }
}
