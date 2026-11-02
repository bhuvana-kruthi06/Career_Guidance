package edu.careerguidance.api;

import edu.careerguidance.service.CareerCatalogService;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/careers")
public class CareerController {
    private final CareerCatalogService catalog;
    public CareerController(CareerCatalogService catalog) { this.catalog = catalog; }

    @GetMapping public CareerCatalogService.SearchResults search(
            @RequestParam(required = false) String query, @RequestParam(required = false) String riasec,
            @RequestParam(defaultValue = "20") int limit, @RequestParam(defaultValue = "0") int offset) {
        try { return catalog.search(query, riasec, limit, offset); }
        catch (IllegalArgumentException e) { throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage()); }
    }
    @GetMapping("/{socCode}") public Map<String, Object> detail(@PathVariable String socCode) {
        if (!socCode.matches("[0-9]{2}-[0-9]{4}\\.[0-9]{2}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a valid O*NET-SOC code.");
        Map<String, Object> career = catalog.detail(socCode);
        if (career == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Career was not found.");
        return career;
    }
}
