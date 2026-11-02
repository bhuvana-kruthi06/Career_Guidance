package edu.careerguidance.api;

import edu.careerguidance.service.CareerRecommendationService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/recommendations")
public class RecommendationController {
    private final CareerRecommendationService recommendations;
    public RecommendationController(CareerRecommendationService recommendations) { this.recommendations = recommendations; }

    @PostMapping public List<Map<String, Object>> recommend(@Valid @RequestBody MatchRequest request) {
        try { return recommendations.recommend(request); }
        catch (IllegalArgumentException error) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, error.getMessage()); }
    }
}
