package edu.careerguidance.api;

import edu.careerguidance.service.ResourceCatalogService;
import java.util.List;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/resources")
public class ResourceController {
    private final ResourceCatalogService catalog;
    public ResourceController(ResourceCatalogService catalog) { this.catalog = catalog; }
    @GetMapping public List<Map<String, Object>> all() { return catalog.all(); }
}
