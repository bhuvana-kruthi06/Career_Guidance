package edu.careerguidance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class ResourceCatalogService {
    private final List<Map<String, Object>> resources;
    public ResourceCatalogService(ObjectMapper mapper, @Value("${career-guidance.project-root:.}") String root) {
        List<Map<String, Object>> loaded = List.of(); Path candidate = Path.of(root).toAbsolutePath().normalize();
        for (int depth = 0; depth < 4; depth++) {
            Path file = candidate.resolve("data/resources/skill_resources.json");
            if (Files.isRegularFile(file)) {
                try { loaded = mapper.readValue(file.toFile(), new TypeReference<>() {}); }
                catch (Exception e) { throw new IllegalStateException("Curated skill resources could not be read."); }
                break;
            }
            if (candidate.getParent() == null) break;
            candidate = candidate.getParent();
        }
        this.resources = loaded;
    }
    public List<Map<String, Object>> all() { return resources; }
}
