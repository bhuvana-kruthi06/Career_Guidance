package edu.careerguidance.auth;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class MeController {
    private final AuthService auth;
    public MeController(AuthService auth) { this.auth = auth; }
    @GetMapping public AuthUserView me(@AuthenticationPrincipal CurrentUser principal) { return auth.currentUser(principal); }
}
