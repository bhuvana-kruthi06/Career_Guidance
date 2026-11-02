package edu.careerguidance.auth;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class AdminRepository {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    public AdminRepository(JdbcTemplate jdbc, ObjectMapper json) { this.jdbc = jdbc; this.json = json; }

    public long countAdmins() { return count("SELECT COUNT(*) FROM users WHERE role='ADMIN' AND account_status='ACTIVE'"); }
    public Map<String, Object> overview() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalUsers", count("SELECT COUNT(*) FROM users WHERE role='USER' AND account_status<>'ARCHIVED'"));
        result.put("newUsersWeek", count("SELECT COUNT(*) FROM users WHERE created_at>=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 7 DAY) AND role='USER'"));
        result.put("newUsersMonth", count("SELECT COUNT(*) FROM users WHERE created_at>=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 30 DAY) AND role='USER'"));
        result.put("activeUsers", count("SELECT COUNT(*) FROM users WHERE account_status='ACTIVE' AND last_active_at>=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 30 DAY) AND role='USER'"));
        result.put("resumeAnalyses", count("SELECT COUNT(*) FROM resume_analyses"));
        result.put("assessments", count("SELECT COUNT(*) FROM career_assessments"));
        result.put("roadmaps", count("SELECT COUNT(*) FROM career_tool_records WHERE record_type='ROADMAP'"));
        result.put("interviewsCompleted", count("SELECT COUNT(*) FROM career_tool_records WHERE record_type='INTERVIEW' AND status='Completed'"));
        result.put("jobApplications", count("SELECT COUNT(*) FROM job_applications"));
        result.put("usersByTargetRole", jdbc.query("SELECT target_role_title AS title,COUNT(*) AS total FROM student_profiles WHERE target_role_title IS NOT NULL GROUP BY target_role_title ORDER BY total DESC LIMIT 8",
                (rs, n) -> Map.of("title", rs.getString("title"), "total", rs.getLong("total"))));
        result.put("usersBySkillCategory", usersBySkillCategory());
        result.put("recentSignups", listUsers(new UserQuery(0, 8, "", "", "", "", "", "", "created", "desc", "")).get("items"));
        result.put("activity", jdbc.query("SELECT DATE(last_active_at) AS day,COUNT(DISTINCT id) AS active FROM users WHERE last_active_at>=DATE_SUB(CURRENT_DATE,INTERVAL 13 DAY) GROUP BY DATE(last_active_at) ORDER BY day",
                (rs, n) -> Map.of("date", rs.getDate("day").toLocalDate().toString(), "activeUsers", rs.getLong("active"))));
        return result;
    }

    public Map<String, Object> listUsers(UserQuery query) {
        StringBuilder where = new StringBuilder(" WHERE 1=1 "); List<Object> params = new ArrayList<>();
        if (!query.search().isBlank()) { where.append(" AND (LOWER(u.email) LIKE ? OR LOWER(COALESCE(u.full_name,'')) LIKE ? OR LOWER(COALESCE(p.profile_json,'')) LIKE ? OR LOWER(COALESCE(p.target_role_title,'')) LIKE ?)"); String term = "%" + query.search().trim().toLowerCase() + "%"; params.add(term); params.add(term); params.add(term); params.add(term); }
        if (!query.role().isBlank()) { where.append(" AND u.role=?"); params.add(query.role()); }
        if (!query.status().isBlank()) { where.append(" AND u.account_status=?"); params.add(query.status()); }
        if (!query.targetRole().isBlank()) { where.append(" AND p.target_role_title=?"); params.add(query.targetRole()); }
        if (!query.from().isBlank()) { where.append(" AND u.created_at>=?"); params.add(query.from()); }
        if (!query.to().isBlank()) { where.append(" AND u.created_at<DATE_ADD(?,INTERVAL 1 DAY)"); params.add(query.to()); }
        if ("active".equals(query.activity())) where.append(" AND u.last_active_at>=DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 30 DAY)");
        if ("inactive".equals(query.activity())) where.append(" AND (u.last_active_at IS NULL OR u.last_active_at<DATE_SUB(CURRENT_TIMESTAMP,INTERVAL 30 DAY))");
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM users u LEFT JOIN student_profiles p ON p.user_id=u.id" + where, Long.class, params.toArray());
        String sort = switch (query.sort()) { case "name" -> "COALESCE(u.full_name,u.email)"; case "email" -> "u.email"; case "active" -> "u.last_active_at"; default -> "u.created_at"; };
        String direction = "asc".equalsIgnoreCase(query.direction()) ? "ASC" : "DESC";
        List<Map<String, Object>> items = jdbc.query("SELECT u.id,u.email,COALESCE(NULLIF(u.full_name,''),'') AS name,u.role,u.account_status AS status,u.created_at,u.last_active_at,p.target_role_title AS target_role FROM users u LEFT JOIN student_profiles p ON p.user_id=u.id" + where + " ORDER BY " + sort + " " + direction + " LIMIT ? OFFSET ?",
                (rs, n) -> { Map<String,Object> row=new LinkedHashMap<>(); row.put("id",rs.getLong("id")); row.put("email",rs.getString("email")); row.put("name",rs.getString("name")); row.put("role",rs.getString("role")); row.put("status",rs.getString("status")); row.put("createdAt",iso(rs.getTimestamp("created_at"))); row.put("lastActiveAt",iso(rs.getTimestamp("last_active_at"))); row.put("targetRole",rs.getString("target_role")); return row; }, append(params, query.size(), query.page()*query.size()).toArray());
        return Map.of("items", items, "page", query.page(), "size", query.size(), "total", total, "pages", total == 0 ? 0 : (total + query.size() - 1) / query.size());
    }

    public Optional<Map<String, Object>> user(long id) {
        List<Map<String,Object>> base=jdbc.query("SELECT id,email,full_name,role,account_status,is_active,created_at,last_active_at FROM users WHERE id=?",
                (rs,n)->{Map<String,Object> row=new LinkedHashMap<>();row.put("id",rs.getLong("id"));row.put("email",rs.getString("email"));row.put("name",rs.getString("full_name"));row.put("role",rs.getString("role"));row.put("status",rs.getString("account_status"));row.put("active",rs.getBoolean("is_active"));row.put("createdAt",iso(rs.getTimestamp("created_at")));row.put("lastActiveAt",iso(rs.getTimestamp("last_active_at")));return row;},id);
        if(base.isEmpty()) return Optional.empty();
        Map<String,Object> result=base.getFirst();
        result.put("profile", jdbc.query("SELECT profile_json,target_role_code,target_role_title,profile_completeness FROM student_profiles WHERE user_id=?",(rs,n)->{Map<String,Object> p=readObject(rs.getString("profile_json"));p.put("targetRole",rs.getString("target_role_title")==null?null:Map.of("code",rs.getString("target_role_code"),"title",rs.getString("target_role_title")));p.put("completeness",rs.getInt("profile_completeness"));return p;},id).stream().findFirst().orElse(Map.of()));
        result.put("assessments", jdbc.query("SELECT id,assessment_json,recommendations_json,created_at FROM career_assessments WHERE user_id=? ORDER BY created_at DESC",(rs,n)->Map.of("id",rs.getLong("id"),"assessment",readObject(rs.getString("assessment_json")),"recommendations",readList(rs.getString("recommendations_json")),"createdAt",iso(rs.getTimestamp("created_at"))),id));
        result.put("resumes", jdbc.query("SELECT id,target_role_code,extracted_json,report_json,created_at FROM resume_analyses WHERE user_id=? ORDER BY created_at DESC",(rs,n)->Map.of("id",rs.getLong("id"),"targetRoleCode",rs.getString("target_role_code")==null?"":rs.getString("target_role_code"),"extracted",readObject(rs.getString("extracted_json")),"report",readObject(rs.getString("report_json")),"createdAt",iso(rs.getTimestamp("created_at"))),id));
        result.put("skillProgress", jdbc.query("SELECT role_code,skill_name,status,updated_at FROM skill_gap_progress WHERE user_id=? ORDER BY updated_at DESC",(rs,n)->Map.of("roleCode",rs.getString("role_code"),"skill",rs.getString("skill_name"),"status",rs.getString("status"),"updatedAt",iso(rs.getTimestamp("updated_at"))),id));
        result.put("roadmaps", toolRecords(id,"ROADMAP",false)); result.put("projects",toolRecords(id,"PROJECT",false));
        result.put("interviews",toolRecords(id,"INTERVIEW",false)); result.put("mentorConversations",toolRecords(id,"MENTOR",true));
        result.put("jobApplications",jdbc.query("SELECT id,company,role_title,status,application_date,follow_up_date,updated_at FROM job_applications WHERE user_id=? ORDER BY updated_at DESC",(rs,n)->Map.of("id",rs.getLong("id"),"company",rs.getString("company"),"role",rs.getString("role_title"),"status",rs.getString("status"),"applicationDate",rs.getDate("application_date")==null?"":rs.getDate("application_date").toString(),"followUpDate",rs.getDate("follow_up_date")==null?"":rs.getDate("follow_up_date").toString(),"updatedAt",iso(rs.getTimestamp("updated_at"))),id));
        result.put("notes",notes(id)); return Optional.of(result);
    }

    public List<Map<String,Object>> records(String type) {
        return switch(type) {
            case "profiles" -> jdbc.query("SELECT u.id AS user_id,u.email,u.full_name,u.account_status,p.profile_completeness,p.target_role_title,p.updated_at FROM student_profiles p JOIN users u ON u.id=p.user_id ORDER BY p.updated_at DESC LIMIT 500",(rs,n)->Map.of("id",rs.getLong("user_id"),"userId",rs.getLong("user_id"),"email",rs.getString("email"),"name",rs.getString("full_name")==null?"":rs.getString("full_name"),"status",rs.getString("account_status"),"completeness",rs.getInt("profile_completeness"),"targetRole",rs.getString("target_role_title")==null?"":rs.getString("target_role_title"),"updatedAt",iso(rs.getTimestamp("updated_at"))));
            case "resumes" -> jdbc.query("SELECT r.id,r.user_id,u.email,r.target_role_code,r.report_json,r.created_at FROM resume_analyses r JOIN users u ON u.id=r.user_id ORDER BY r.created_at DESC LIMIT 500",(rs,n)->{Map<String,Object> report=readObject(rs.getString("report_json"));return Map.of("id",rs.getLong("id"),"userId",rs.getLong("user_id"),"email",rs.getString("email"),"targetRole",rs.getString("target_role_code")==null?"":rs.getString("target_role_code"),"atsScore",report.getOrDefault("atsScore",0),"createdAt",iso(rs.getTimestamp("created_at")));});
            case "assessments" -> jdbc.query("SELECT a.id,a.user_id,u.email,a.recommendations_json,a.created_at FROM career_assessments a JOIN users u ON u.id=a.user_id ORDER BY a.created_at DESC LIMIT 500",(rs,n)->Map.of("id",rs.getLong("id"),"userId",rs.getLong("user_id"),"email",rs.getString("email"),"recommendationCount",readList(rs.getString("recommendations_json")).size(),"createdAt",iso(rs.getTimestamp("created_at"))));
            case "roadmaps" -> toolRows("ROADMAP");
            case "applications" -> jdbc.query("SELECT a.id,a.user_id,u.email,a.company,a.role_title,a.status,a.application_date,a.updated_at FROM job_applications a JOIN users u ON u.id=a.user_id ORDER BY a.updated_at DESC LIMIT 500",(rs,n)->Map.of("id",rs.getLong("id"),"userId",rs.getLong("user_id"),"email",rs.getString("email"),"company",rs.getString("company"),"role",rs.getString("role_title"),"status",rs.getString("status"),"applicationDate",rs.getDate("application_date")==null?"":rs.getDate("application_date").toString(),"updatedAt",iso(rs.getTimestamp("updated_at"))));
            default -> throw new IllegalArgumentException("Unsupported admin records category.");
        };
    }

    public void updateStatus(long id,String status) { jdbc.update("UPDATE users SET account_status=?,is_active=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",status,"ACTIVE".equals(status),id); if(!"ACTIVE".equals(status))jdbc.update("UPDATE refresh_tokens SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL",id); }
    public void updateRole(long id,String role) { jdbc.update("UPDATE users SET role=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",role,id); jdbc.update("UPDATE refresh_tokens SET revoked_at=CURRENT_TIMESTAMP WHERE user_id=? AND revoked_at IS NULL",id); }
    public void updateBasic(long id,String name,String email) { jdbc.update("UPDATE users SET full_name=?,email=?,updated_at=CURRENT_TIMESTAMP WHERE id=?",name,email,id); jdbc.update("UPDATE student_profiles SET profile_json=JSON_SET(profile_json,'$.name',?) WHERE user_id=?",name,id); }
    public void addNote(long userId,long adminId,String note) { jdbc.update("INSERT INTO admin_user_notes(user_id,admin_id,note) VALUES(?,?,?)",userId,adminId,note); }
    public List<Map<String,Object>> notes(long userId) { return jdbc.query("SELECT n.id,n.note,n.created_at,u.email AS author FROM admin_user_notes n JOIN users u ON u.id=n.admin_id WHERE n.user_id=? ORDER BY n.created_at DESC",(rs,n)->Map.of("id",rs.getLong("id"),"note",rs.getString("note"),"createdAt",iso(rs.getTimestamp("created_at")),"author",rs.getString("author")),userId); }
    public void audit(long adminId,String action,String subjectType,String subjectId,Object details) { try { jdbc.update("INSERT INTO admin_audit_logs(admin_id,action,subject_type,subject_id,details_json) VALUES(?,?,?,?,?)",adminId,action,subjectType,subjectId,json.writeValueAsString(details)); } catch(Exception e) { throw new IllegalArgumentException("Could not record admin action."); } }
    public List<Map<String,Object>> auditLogs(int limit) { return jdbc.query("SELECT a.id,a.action,a.subject_type,a.subject_id,a.details_json,a.created_at,u.email AS admin FROM admin_audit_logs a JOIN users u ON u.id=a.admin_id ORDER BY a.created_at DESC LIMIT ?",(rs,n)->Map.of("id",rs.getLong("id"),"action",rs.getString("action"),"subjectType",rs.getString("subject_type"),"subjectId",rs.getString("subject_id"),"details",readObject(rs.getString("details_json")),"createdAt",iso(rs.getTimestamp("created_at")),"admin",rs.getString("admin")),limit); }

    public List<Map<String,Object>> content(String type) { return jdbc.query("SELECT id,content_type,title,content_json,enabled,archived_at,created_at,updated_at FROM admin_content_items WHERE (?='' OR content_type=?) ORDER BY updated_at DESC",(rs,n)->contentRow(rs),type,type); }
    public Map<String,Object> content(long id) { return jdbc.query("SELECT id,content_type,title,content_json,enabled,archived_at,created_at,updated_at FROM admin_content_items WHERE id=?",(rs,n)->contentRow(rs),id).stream().findFirst().orElseThrow(()->new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Content item was not found.")); }
    public long createContent(String type,String title,Object body,boolean enabled) { String value=write(body); var key=new org.springframework.jdbc.support.GeneratedKeyHolder(); jdbc.update(c->{var s=c.prepareStatement("INSERT INTO admin_content_items(content_type,title,content_json,enabled) VALUES(?,?,?,?)",new String[]{"id"});s.setString(1,type);s.setString(2,title);s.setString(3,value);s.setBoolean(4,enabled);return s;},key); return key.getKey().longValue(); }
    public void updateContent(long id,String type,String title,Object body,boolean enabled) { if(jdbc.update("UPDATE admin_content_items SET content_type=?,title=?,content_json=?,enabled=? WHERE id=? AND archived_at IS NULL",type,title,write(body),enabled,id)==0)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Content item was not found."); }
    public void archiveContent(long id) { if(jdbc.update("UPDATE admin_content_items SET enabled=FALSE,archived_at=CURRENT_TIMESTAMP WHERE id=? AND archived_at IS NULL",id)==0)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Content item was not found."); }
    public void enableContent(long id,boolean enabled) { if(jdbc.update("UPDATE admin_content_items SET enabled=? WHERE id=? AND archived_at IS NULL",enabled,id)==0)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,"Content item was not found."); }
    public long count(String sql) { Long value=jdbc.queryForObject(sql,Long.class); return value==null?0:value; }

    private List<Map<String,Object>> toolRecords(long userId,String type,boolean metadataOnly) { return jdbc.query("SELECT id,record_key,status,record_json,updated_at FROM career_tool_records WHERE user_id=? AND record_type=? ORDER BY updated_at DESC",(rs,n)->{Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getLong("id"));m.put("key",rs.getString("record_key"));m.put("status",rs.getString("status"));m.put("updatedAt",iso(rs.getTimestamp("updated_at")));if(!metadataOnly)m.put("data",readObject(rs.getString("record_json")));else{Map<String,Object>d=readObject(rs.getString("record_json"));m.put("title",d.getOrDefault("title","Mentor conversation"));m.put("messageCount",d.getOrDefault("messageCount",0));}return m;},userId,type); }
    private List<Map<String,Object>> toolRows(String type) { return jdbc.query("SELECT r.id,r.user_id,u.email,r.status,r.updated_at FROM career_tool_records r JOIN users u ON u.id=r.user_id WHERE r.record_type=? ORDER BY r.updated_at DESC LIMIT 500",(rs,n)->Map.of("id",rs.getLong("id"),"userId",rs.getLong("user_id"),"email",rs.getString("email"),"status",rs.getString("status")==null?"":rs.getString("status"),"updatedAt",iso(rs.getTimestamp("updated_at"))),type); }
    private List<Map<String,Object>> usersBySkillCategory() { Map<String,Integer> counts=new LinkedHashMap<>(); jdbc.query("SELECT profile_json FROM student_profiles",rs->{Map<String,Object> profile=readObject(rs.getString(1));Object raw=profile.get("skills");if(raw instanceof Map<?,?> skills)for(Object key:skills.keySet())if(key instanceof String name){String lower=name.toLowerCase();String category=lower.contains("sql")||lower.contains("database")?"Database":lower.contains("cloud")?"Cloud":lower.contains("machine")||lower.contains("ai")?"AI/ML":lower.contains("communication")||lower.contains("leadership")?"Soft skills":lower.contains("java")||lower.contains("python")||lower.contains("programming")?"Backend":"Other";counts.merge(category,1,Integer::sum);}}); return counts.entrySet().stream().map(e->Map.<String,Object>of("category",e.getKey(),"total",e.getValue())).toList(); }
    private Map<String,Object> contentRow(java.sql.ResultSet rs) throws java.sql.SQLException { Map<String,Object> m=new LinkedHashMap<>();m.put("id",rs.getLong("id"));m.put("type",rs.getString("content_type"));m.put("title",rs.getString("title"));m.put("content",readObject(rs.getString("content_json")));m.put("enabled",rs.getBoolean("enabled"));m.put("archived",rs.getTimestamp("archived_at")!=null);m.put("createdAt",iso(rs.getTimestamp("created_at")));m.put("updatedAt",iso(rs.getTimestamp("updated_at")));return m; }
    private Map<String,Object> readObject(String value) { try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){return Map.of();} }
    private List<Object> readList(String value) { try{return json.readValue(value,new TypeReference<>(){});}catch(Exception e){return List.of();} }
    private String write(Object value) { try{return json.writeValueAsString(value);}catch(Exception e){throw new IllegalArgumentException("Invalid JSON content.");} }
    private static String iso(Timestamp value) { return value==null?"":value.toInstant().toString(); }
    private static List<Object> append(List<Object> base,Object... values) { List<Object> result=new ArrayList<>(base);for(Object v:values)result.add(v);return result; }
    public record UserQuery(int page,int size,String search,String role,String status,String targetRole,String from,String to,String sort,String direction,String activity) { }
}
