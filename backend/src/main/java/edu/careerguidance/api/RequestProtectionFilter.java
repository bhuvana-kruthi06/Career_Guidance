package edu.careerguidance.api;

import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class RequestProtectionFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(RequestProtectionFilter.class);
    private static final Pattern SAFE_REQUEST_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final int MAX_CLIENT_BUCKETS = 10_000;

    private final int rateCapacity;
    private final int loginRateCapacity;
    private final int registrationRateCapacity;
    private final int maxBodyBytes;
    private final int assessmentMaxBodyBytes;
    private final Semaphore matcherSlots;
    private final Map<String, Bucket> matchBuckets = new LinkedHashMap<>(128, 0.75f, true);
    private final Map<String, Bucket> loginBuckets = new LinkedHashMap<>(128, 0.75f, true);
    private final Map<String, Bucket> registrationBuckets = new LinkedHashMap<>(128, 0.75f, true);

    public RequestProtectionFilter(
            @Value("${career-guidance.rate-limit.capacity:20}") int rateCapacity,
            @Value("${career-guidance.auth.login-rate-capacity:5}") int loginRateCapacity,
            @Value("${career-guidance.auth.registration-rate-capacity:5}") int registrationRateCapacity,
            @Value("${career-guidance.request.max-body-bytes:16384}") int maxBodyBytes,
            @Value("${career-guidance.request.assessment-max-body-bytes:2097152}") int assessmentMaxBodyBytes,
            @Value("${career-guidance.matcher.max-concurrent:4}") int maxConcurrent) {
        this.rateCapacity = Math.max(1, rateCapacity);
        this.loginRateCapacity = Math.max(1, loginRateCapacity);
        this.registrationRateCapacity = Math.max(1, registrationRateCapacity);
        this.maxBodyBytes = Math.max(1024, maxBodyBytes);
        this.assessmentMaxBodyBytes = Math.max(this.maxBodyBytes, assessmentMaxBodyBytes);
        this.matcherSlots = new Semaphore(Math.max(1, maxConcurrent), true);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String suppliedId = request.getHeader("X-Request-ID");
        String requestId = suppliedId != null && SAFE_REQUEST_ID.matcher(suppliedId).matches()
                ? suppliedId : UUID.randomUUID().toString();
        request.setAttribute("requestId", requestId);
        response.setHeader("X-Request-ID", requestId);
        MDC.put("requestId", requestId);
        boolean apiPost = "POST".equalsIgnoreCase(request.getMethod()) && request.getRequestURI().startsWith("/api/");
        boolean resumeUpload = "/api/me/resumes".equals(request.getRequestURI())
                && request.getContentType() != null && request.getContentType().toLowerCase(java.util.Locale.ROOT).startsWith("multipart/form-data");
        boolean matcherRequest = apiPost && ("/api/matches".equals(request.getRequestURI()) || "/api/what-if".equals(request.getRequestURI()));
        boolean jobDescriptionRequest = apiPost && "/api/me/job-descriptions".equals(request.getRequestURI());
        boolean interviewRequest = apiPost && request.getRequestURI().startsWith("/api/me/interviews");
        boolean mentorRequest = apiPost && "/api/me/mentor".equals(request.getRequestURI());
        boolean assessmentRequest = apiPost && "/api/me/assessments".equals(request.getRequestURI());
        int bodyLimit = assessmentRequest ? assessmentMaxBodyBytes : jobDescriptionRequest ? 64 * 1024 : interviewRequest ? 32 * 1024 : maxBodyBytes;
        String oversizedMessage = jobDescriptionRequest ? "This job description request is too large. Keep it under 12,000 characters."
                : interviewRequest ? "This interview request is too large. Shorten the answer and try again."
                : assessmentRequest ? "This assessment submission exceeds the configured size limit. Shorten unusually large text entries and try again."
                : "The request is too large. Reduce the amount of submitted data.";
        boolean loginRequest = apiPost && "/api/auth/login".equals(request.getRequestURI());
        boolean registrationRequest = apiPost && "/api/auth/register".equals(request.getRequestURI());
        boolean acquiredMatcherSlot = false;
        long started = System.nanoTime();
        try {
            if (apiPost && resumeUpload) {
                if (request.getContentLengthLong() > 6L * 1024 * 1024) {
                    writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Upload a PDF smaller than 5 MB.");
                    return;
                }
                chain.doFilter(request, response);
            } else if (apiPost) {
                if (request.getContentLengthLong() > bodyLimit) {
                    writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, oversizedMessage);
                    return;
                }
                byte[] body = request.getInputStream().readNBytes(bodyLimit + 1);
                if (body.length > bodyLimit) {
                    writeError(response, HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, oversizedMessage);
                    return;
                }
                if (loginRequest && !bucketFor(loginBuckets, request.getRemoteAddr(), loginRateCapacity, Duration.ofMinutes(15)).tryConsume(1)) {
                    response.setHeader("Retry-After", "900");
                    writeError(response, 429, "Too many sign-in attempts. Wait 15 minutes and try again.");
                    return;
                }
                if (registrationRequest && !bucketFor(registrationBuckets, request.getRemoteAddr(), registrationRateCapacity, Duration.ofHours(1)).tryConsume(1)) {
                    response.setHeader("Retry-After", "3600");
                    writeError(response, 429, "Too many account creation attempts. Wait one hour and try again.");
                    return;
                }
                if ((interviewRequest || mentorRequest) && !bucketFor(matchBuckets, "career-tools:" + request.getRemoteAddr(), rateCapacity, Duration.ofMinutes(1)).tryConsume(1)) {
                    response.setHeader("Retry-After", "3");
                    writeError(response, 429, "You have made too many AI career-tool requests. Wait a moment and try again.");
                    return;
                }
                if (matcherRequest && !bucketFor(matchBuckets, request.getRemoteAddr(), rateCapacity, Duration.ofMinutes(1)).tryConsume(1)) {
                    response.setHeader("Retry-After", "3");
                    writeError(response, 429, "You have made too many match requests. Wait a moment and try again.");
                    return;
                }
                if (matcherRequest && !matcherSlots.tryAcquire()) {
                    response.setHeader("Retry-After", "5");
                    writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                            "The matching service is handling too many requests. Try again shortly.");
                    return;
                }
                acquiredMatcherSlot = matcherRequest;
                chain.doFilter(new BoundedBodyRequest(request, body), response);
            } else {
                chain.doFilter(request, response);
            }
        } finally {
            if (acquiredMatcherSlot) matcherSlots.release();
            long durationMs = Duration.ofNanos(System.nanoTime() - started).toMillis();
            log.info("http_request method={} path={} status={} durationMs={} requestId={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), durationMs, requestId);
            MDC.remove("requestId");
        }
    }

    private synchronized Bucket bucketFor(Map<String, Bucket> registry, String client, int capacity, Duration refillPeriod) {
        Bucket bucket = registry.get(client);
        if (bucket != null) return bucket;
        bucket = Bucket.builder().addLimit(limit -> limit.capacity(capacity)
                .refillGreedy(capacity, refillPeriod)).build();
        registry.put(client, bucket);
        if (registry.size() > MAX_CLIENT_BUCKETS) registry.remove(registry.keySet().iterator().next());
        return bucket;
    }

    private static void writeError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json");
        response.getWriter().write("{\"error\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}");
    }

    private static final class BoundedBodyRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private final ByteArrayInputStream input;
        private BoundedBodyRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; this.input = new ByteArrayInputStream(body); }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public ServletInputStream getInputStream() {
            return new ServletInputStream() {
                @Override public int read() { return input.read(); }
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException("Async request bodies are not supported."); }
            };
        }
        @Override public BufferedReader getReader() { return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8)); }
    }
}
