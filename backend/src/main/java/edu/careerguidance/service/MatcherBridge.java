package edu.careerguidance.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class MatcherBridge {
    private static final Logger log = LoggerFactory.getLogger(MatcherBridge.class);
    private final ObjectMapper mapper;
    private final Path projectRoot;
    private final String python;

    public MatcherBridge(ObjectMapper mapper,
            @Value("${career-guidance.project-root:..}") String projectRoot,
            @Value("${career-guidance.python-executable:python}") String python) {
        this.mapper = mapper;
        this.projectRoot = Path.of(projectRoot).toAbsolutePath().normalize();
        this.python = python;
    }

    public JsonNode skills() { return invoke(Map.of("operation", "skills")); }
    public JsonNode match(Map<String, Object> request) { return invoke(request); }

    private JsonNode invoke(Map<String, Object> request) {
        long started = System.nanoTime();
        String operation = String.valueOf(request.getOrDefault("operation", "match"));
        String outcome = "error";
        try {
            Path executable = Path.of(python);
            if (!executable.isAbsolute()) executable = projectRoot.resolve(executable).normalize();
            Path script = projectRoot.resolve("backend/matcher_cli.py");
            Process process = new ProcessBuilder(executable.toString(), script.toString())
                    .directory(projectRoot.toFile()).start();
            CompletableFuture<String> stdout = CompletableFuture.supplyAsync(() -> read(process.getInputStream()));
            CompletableFuture<String> stderr = CompletableFuture.supplyAsync(() -> read(process.getErrorStream()));
            try (var input = process.getOutputStream()) { mapper.writeValue(input, request); }
            if (!process.waitFor(90, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new MatcherUnavailableException("Career matching timed out.");
            }
            String output = stdout.get(5, TimeUnit.SECONDS);
            String errors = stderr.get(5, TimeUnit.SECONDS);
            if (process.exitValue() != 0) {
                log.warn("matcher_process_failed operation={} requestId={}", operation, MDC.get("requestId"));
                throw new MatcherUnavailableException("The career matcher could not process this request. Check the backend log.");
            }
            JsonNode response = mapper.readTree(output);
            outcome = "success";
            return response;
        } catch (MatcherUnavailableException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("matcher_process_unavailable operation={} errorType={} requestId={}", operation,
                    ex.getClass().getSimpleName(), MDC.get("requestId"));
            throw new MatcherUnavailableException("The career matcher is temporarily unavailable. Check PROJECT_ROOT and PYTHON_EXECUTABLE.", ex);
        } finally {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
            log.info("matcher_call operation={} outcome={} durationMs={} requestId={}",
                    operation, outcome, durationMs, MDC.get("requestId"));
        }
    }

    private static String read(java.io.InputStream stream) {
        try { return new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8); }
        catch (IOException ex) { throw new IllegalStateException(ex); }
    }
}
