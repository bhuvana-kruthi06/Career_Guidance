package edu.careerguidance.api;

import com.fasterxml.jackson.databind.JsonNode;
import edu.careerguidance.service.MatcherBridge;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final MatcherBridge matcher;
    public HealthController(MatcherBridge matcher) { this.matcher = matcher; }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        try {
            JsonNode skills = matcher.skills();
            if (skills == null || !skills.isArray() || skills.isEmpty()) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(Map.of("status", "DOWN", "matcher", "DOWN"));
            }
            return ResponseEntity.ok(Map.of("status", "UP", "matcher", "UP"));
        } catch (RuntimeException ex) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("status", "DOWN", "matcher", "DOWN"));
        }
    }
}
