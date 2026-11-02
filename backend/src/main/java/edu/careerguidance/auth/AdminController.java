package edu.careerguidance.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
@RequestMapping("/api/admin")
@Validated
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class AdminController {
    private final AdminRepository admin;
    public AdminController(AdminRepository admin) { this.admin = admin; }

    @GetMapping("/status") public Map<String, String> status() { return Map.of("status", "ADMIN_ACCESS_GRANTED"); }
    @GetMapping("/overview") public Map<String,Object> overview() { return admin.overview(); }
    @GetMapping("/records") public Map<String,Object> records(@RequestParam @Pattern(regexp="profiles|resumes|assessments|roadmaps|applications") String type) { return Map.of("items",admin.records(type)); }
    @GetMapping("/users") public Map<String,Object> users(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size,@RequestParam(defaultValue="") String search,
            @RequestParam(defaultValue="") String role,@RequestParam(defaultValue="") String status,
            @RequestParam(defaultValue="") String targetRole,@RequestParam(defaultValue="") String from,
            @RequestParam(defaultValue="") String to,@RequestParam(defaultValue="created") String sort,
            @RequestParam(defaultValue="desc") String direction,@RequestParam(defaultValue="") String activity) {
        if(page<0||size<1||size>100) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid page or page size.");
        if(!role.isBlank()&&!role.matches("USER|ADMIN")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid role filter.");
        if(!status.isBlank()&&!status.matches("ACTIVE|SUSPENDED|ARCHIVED")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid status filter.");
        date(from);date(to);
        return admin.listUsers(new AdminRepository.UserQuery(page,size,search,role,status,targetRole,from,to,sort,direction,activity));
    }
    @GetMapping("/users/{id}") public Map<String,Object> user(@PathVariable long id) { return admin.user(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User was not found.")); }
    @PutMapping("/users/{id}") public Map<String,Object> editUser(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id,@Valid @RequestBody EditUser body) {
        requireUser(id); admin.updateBasic(id,body.name(),body.email().trim().toLowerCase()); admin.audit(actor.id(),"USER_EDITED","USER",Long.toString(id),Map.of("email",body.email())); return user(id);
    }
    @PutMapping("/users/{id}/status") public Map<String,Object> status(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id,@Valid @RequestBody StatusChange body) {
        requireUser(id); if(id==actor.id()) throw new ResponseStatusException(HttpStatus.CONFLICT,"You cannot change your own account status.");
        if(!body.confirmed()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Explicit confirmation is required.");
        if("ARCHIVED".equals(body.status())&&"ADMIN".equals(user(id).get("role"))&&admin.countAdmins()<=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot archive the last active administrator.");
        admin.updateStatus(id,body.status()); admin.audit(actor.id(),"USER_STATUS_CHANGED","USER",Long.toString(id),Map.of("status",body.status())); return user(id);
    }
    @PutMapping("/users/{id}/role") public Map<String,Object> role(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id,@Valid @RequestBody RoleChange body) {
        requireUser(id); if(!body.confirmed()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Explicit confirmation is required.");
        if(id==actor.id()&&!"ADMIN".equals(body.role())) throw new ResponseStatusException(HttpStatus.CONFLICT,"You cannot remove your own administrator role.");
        if("ADMIN".equals(user(id).get("role"))&&"USER".equals(body.role())&&admin.countAdmins()<=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot demote the last active administrator.");
        admin.updateRole(id,body.role()); admin.audit(actor.id(),"USER_ROLE_CHANGED","USER",Long.toString(id),Map.of("role",body.role())); return user(id);
    }
    @DeleteMapping("/users/{id}") public Map<String,String> archive(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id,@Valid @RequestBody ConfirmAction body) {
        requireUser(id); if(id==actor.id()) throw new ResponseStatusException(HttpStatus.CONFLICT,"You cannot archive your own account.");
        if(!body.confirmed()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Explicit confirmation is required.");
        if("ADMIN".equals(user(id).get("role"))&&admin.countAdmins()<=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Cannot archive the last active administrator.");
        admin.updateStatus(id,"ARCHIVED"); admin.audit(actor.id(),"USER_ARCHIVED","USER",Long.toString(id),Map.of()); return Map.of("status","ARCHIVED");
    }
    @PostMapping("/users/{id}/notes") public Map<String,Object> addNote(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id,@Valid @RequestBody Note body) {
        requireUser(id); admin.addNote(id,actor.id(),body.note().trim()); admin.audit(actor.id(),"USER_NOTE_ADDED","USER",Long.toString(id),Map.of()); return Map.of("notes",admin.user(id).orElseThrow().get("notes"));
    }
    @GetMapping("/audit-logs") public Map<String,Object> audits(@RequestParam(defaultValue="50") int limit) { return Map.of("items",admin.auditLogs(Math.min(200,Math.max(1,limit)))); }

    @GetMapping("/content") public Map<String,Object> content(@RequestParam(defaultValue="") String type) { validateType(type,true); return Map.of("items",admin.content(type)); }
    @GetMapping("/content/{id}") public Map<String,Object> contentItem(@PathVariable long id) { return admin.content(id); }
    @PostMapping("/content") public Map<String,Object> createContent(@AuthenticationPrincipal CurrentUser actor,@Valid @RequestBody ContentItem body) {
        validateType(body.type(),false); long id=admin.createContent(body.type(),body.title().trim(),body.content(),body.enabled()); admin.audit(actor.id(),"CONTENT_CREATED",body.type(),Long.toString(id),Map.of("title",body.title())); return admin.content(id);
    }
    @PutMapping("/content/{id}") public Map<String,Object> editContent(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id,@Valid @RequestBody ContentItem body) {
        validateType(body.type(),false); admin.updateContent(id,body.type(),body.title().trim(),body.content(),body.enabled()); admin.audit(actor.id(),"CONTENT_UPDATED",body.type(),Long.toString(id),Map.of("title",body.title())); return admin.content(id);
    }
    @PatchMapping("/content/{id}/enabled") public Map<String,Object> enableContent(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id,@Valid @RequestBody EnabledChange body) {
        admin.enableContent(id,body.enabled()); admin.audit(actor.id(),"CONTENT_ENABLED_CHANGED","CONTENT",Long.toString(id),Map.of("enabled",body.enabled())); return admin.content(id);
    }
    @DeleteMapping("/content/{id}") public Map<String,String> archiveContent(@AuthenticationPrincipal CurrentUser actor,@PathVariable long id,@Valid @RequestBody ConfirmAction body) {
        if(!body.confirmed()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Explicit confirmation is required."); Map<String,Object> existing=admin.content(id); admin.archiveContent(id); admin.audit(actor.id(),"CONTENT_ARCHIVED",String.valueOf(existing.get("type")),Long.toString(id),Map.of()); return Map.of("status","ARCHIVED");
    }
    private Map<String,Object> requireUser(long id) { return admin.user(id).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"User was not found.")); }
    private static void validateType(String type,boolean optional) { if(optional&&type.isBlank())return; if(!java.util.Set.of("CAREER_ROLE","SKILL","ASSESSMENT_QUESTION","ROADMAP_TEMPLATE","PROJECT","LEARNING_RESOURCE").contains(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Unsupported content type."); }
    private static void date(String value) { if(value.isBlank())return; try{LocalDate.parse(value);}catch(Exception ex){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Dates must use YYYY-MM-DD.");} }
    public record EditUser(@NotBlank @Size(max=80) String name,@NotBlank @Email @Size(max=254) String email) { }
    public record StatusChange(@NotBlank @Pattern(regexp="ACTIVE|SUSPENDED|ARCHIVED") String status,boolean confirmed) { }
    public record RoleChange(@NotBlank @Pattern(regexp="USER|ADMIN") String role,boolean confirmed) { }
    public record ConfirmAction(boolean confirmed) { }
    public record Note(@NotBlank @Size(max=2000) String note) { }
    public record EnabledChange(boolean enabled) { }
    public record ContentItem(@NotBlank @Pattern(regexp="CAREER_ROLE|SKILL|ASSESSMENT_QUESTION|ROADMAP_TEMPLATE|PROJECT|LEARNING_RESOURCE") String type,
            @NotBlank @Size(max=200) String title,@NotNull Map<String,Object> content,boolean enabled) { }
}
