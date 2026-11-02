package edu.careerguidance.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.careerguidance.auth.JwtAuthenticationFilter;
import edu.careerguidance.auth.JwtService;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

@Configuration
@ConditionalOnProperty(prefix = "career-guidance.auth", name = "enabled", havingValue = "true")
public class AuthSecurityConfiguration {
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }
    @Bean public UserDetailsService tokenOnlyUserDetailsService() {
        return username -> { throw new UsernameNotFoundException("This API authenticates signed bearer tokens."); };
    }

    @Bean
    public CookieCsrfTokenRepository csrfTokenRepository(@Value("${career-guidance.auth.cookie-secure:false}") boolean secureCookie) {
        CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Strict").secure(secureCookie));
        return repository;
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService, edu.careerguidance.auth.AuthRepository users) {
        return new JwtAuthenticationFilter(jwtService, users);
    }

    @Bean
    public FilterRegistrationBean<JwtAuthenticationFilter> disableJwtServletRegistration(JwtAuthenticationFilter filter) {
        FilterRegistrationBean<JwtAuthenticationFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setEnabled(false);
        return registration;
    }

    @Bean
    public SecurityFilterChain authenticatedSecurity(HttpSecurity http, JwtAuthenticationFilter jwtFilter,
            CookieCsrfTokenRepository csrfRepository, ObjectMapper mapper) throws Exception {
        http.cors(Customizer.withDefaults())
                .csrf(csrf -> csrf.csrfTokenRepository(csrfRepository)
                        .ignoringRequestMatchers("/api/matches", "/api/recommendations", "/api/what-if", "/api/auth/register", "/api/auth/login"))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/health", "/api/skills", "/api/matches", "/api/auth/**").permitAll()
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/me/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, exception) -> writeSecurityError(mapper, response, 401, "Authentication is required."))
                        .accessDeniedHandler((request, response, exception) -> writeSecurityError(mapper, response, 403, "You are not allowed to access this resource.")))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private static void writeSecurityError(ObjectMapper mapper, jakarta.servlet.http.HttpServletResponse response,
            int status, String message) throws java.io.IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(java.nio.charset.StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getOutputStream(), Map.of("error", message));
    }
}
