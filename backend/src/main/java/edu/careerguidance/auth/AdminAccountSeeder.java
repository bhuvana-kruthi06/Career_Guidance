package edu.careerguidance.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class AdminAccountSeeder implements ApplicationRunner {
    private final AuthRepository accounts;
    private final PasswordEncoder passwords;
    private final String email;
    private final String password;
    private final String name;

    public AdminAccountSeeder(AuthRepository accounts, PasswordEncoder passwords,
            @Value("${ADMIN_EMAIL:}") String email, @Value("${ADMIN_PASSWORD:}") String password,
            @Value("${ADMIN_NAME:}") String name) {
        this.accounts=accounts;this.passwords=passwords;this.email=email;this.password=password;this.name=name;
    }

    @Override public void run(ApplicationArguments args) {
        if(email.isBlank()&&password.isBlank()&&name.isBlank()) return;
        if(email.isBlank()||password.isBlank()||name.isBlank()) throw new IllegalStateException("Set ADMIN_EMAIL, ADMIN_PASSWORD, and ADMIN_NAME together to seed the initial administrator.");
        if(password.length()<12||password.length()>128) throw new IllegalStateException("ADMIN_PASSWORD must be between 12 and 128 characters.");
        String normalized=email.trim().toLowerCase(java.util.Locale.ROOT);
        var existing=accounts.findUserByEmail(normalized);
        if(existing.isPresent()) {
            if(!"ADMIN".equals(existing.get().role())) throw new IllegalStateException("ADMIN_EMAIL belongs to a non-admin account; use a dedicated administrator email.");
            return;
        }
        accounts.createAdmin(normalized,passwords.encode(password),name.trim());
    }
}
