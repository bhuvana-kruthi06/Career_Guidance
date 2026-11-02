package edu.careerguidance.api;

import edu.careerguidance.auth.AuthService;
import edu.careerguidance.service.MatcherUnavailableException;
import edu.careerguidance.service.CareerCatalogService;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;

@RestControllerAdvice
public class ApiExceptionHandler {
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> validation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        if (request.getRequestURI().equals("/api/auth/register")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Provide a valid email and a password of at least 12 characters."));
        }
        if (request.getRequestURI().equals("/api/auth/login")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Enter a valid email and password."));
        }
        if (request.getRequestURI().equals("/api/me/profile")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Check profile fields and list limits, then save again."));
        }
        if (request.getRequestURI().equals("/api/me/assessments")) {
            return ResponseEntity.badRequest().body(Map.of("error", "The assessment data or role recommendations were invalid. Review your profile and retry."));
        }
        if (request.getRequestURI().equals("/api/me/target-role")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Select a valid career role from the recommendation list."));
        }
        if (request.getRequestURI().equals("/api/me/skill-gaps/progress")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Choose a listed role skill and set its status to Learning or Learned."));
        }
        return ResponseEntity.badRequest().body(Map.of("error", "Provide at least one skill with a rating from 1 to 5."));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> uploadTooLarge(MaxUploadSizeExceededException ex) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("error", "Choose a PDF resume smaller than 5 MB."));
    }

    @ExceptionHandler(MatcherUnavailableException.class)
    public ResponseEntity<Map<String, String>> matcher(MatcherUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(CareerCatalogService.CatalogUnavailableException.class)
    public ResponseEntity<Map<String, String>> catalogUnavailable(CareerCatalogService.CatalogUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> status(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode()).body(Map.of("error", ex.getReason() == null ? "Request rejected." : ex.getReason()));
    }

    @ExceptionHandler(AuthService.InvalidCredentialsException.class)
    public ResponseEntity<Map<String, String>> invalidCredentials(AuthService.InvalidCredentialsException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Email or password is incorrect."));
    }

    @ExceptionHandler(AuthService.InvalidRefreshTokenException.class)
    public ResponseEntity<Map<String, String>> invalidRefresh(AuthService.InvalidRefreshTokenException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "Your sign-in has expired. Please sign in again."));
    }

    @ExceptionHandler(AuthService.AccountAlreadyExistsException.class)
    public ResponseEntity<Map<String, String>> existingAccount(AuthService.AccountAlreadyExistsException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "An account already exists for this email."));
    }
}
