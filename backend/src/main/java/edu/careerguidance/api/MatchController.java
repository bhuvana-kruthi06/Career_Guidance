package edu.careerguidance.api;

import edu.careerguidance.service.MatcherBridge;
import edu.careerguidance.auth.AccountDataRepository;
import edu.careerguidance.auth.CurrentUser;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.Set;
import java.util.List;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api")
public class MatchController {
    private static final Set<String> RIASEC = Set.of("R", "I", "A", "S", "E", "C");
    private final MatcherBridge matcher;
    private final ObjectProvider<AccountDataRepository> accountData;
    private final ObjectMapper mapper;

    public MatchController(MatcherBridge matcher, ObjectProvider<AccountDataRepository> accountData, ObjectMapper mapper) {
        this.matcher = matcher; this.accountData = accountData; this.mapper = mapper;
    }

    @GetMapping("/skills")
    public Object skills() { return matcher.skills(); }

    @PostMapping("/matches")
    public Object match(@Valid @RequestBody MatchRequest request, @AuthenticationPrincipal CurrentUser user) {
        if (request.isAssessedOnly() && request.getStudentSkills().size() < 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rate at least three skills to create a quick profile.");
        }
        if (request.getStudentInterests() == null || request.getStudentInterests().isEmpty()
                || request.getStudentInterests().size() > 3) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose between one and three work interests.");
        }
        if (request.getStudentInterests() != null && request.getStudentInterests().stream()
                .anyMatch(code -> code == null || !RIASEC.contains(code.trim().toUpperCase()))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Interests must use R, I, A, S, E, or C.");
        }
        JsonNode results = matcher.match(Map.of(
                "student_skills", request.getStudentSkills(),
                "student_interests", request.getStudentInterests() == null ? Set.of() : request.getStudentInterests(),
                "top_n", request.getTopN() == null ? 10 : request.getTopN(),
                "assessed_only", request.isAssessedOnly()));
        if (user != null && results.isArray()) {
            List<Map<String, Object>> records = mapper.convertValue(results, new TypeReference<>() {});
            accountData.ifAvailable(repository -> repository.saveMatch(user.id(), request.getStudentSkills(), request.getStudentInterests(), records));
        }
        return results;
    }
}
