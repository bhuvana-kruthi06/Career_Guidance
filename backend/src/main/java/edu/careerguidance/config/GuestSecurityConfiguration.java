package edu.careerguidance.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "false", matchIfMissing = true)
public class GuestSecurityConfiguration {
    @Bean public UserDetailsService guestOnlyUserDetailsService() {
        return username -> { throw new UsernameNotFoundException("Guest mode does not create local accounts."); };
    }

    @Bean
    public SecurityFilterChain guestSecurity(HttpSecurity http) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
