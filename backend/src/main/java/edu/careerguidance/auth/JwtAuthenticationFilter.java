package edu.careerguidance.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final AuthRepository users;
    public JwtAuthenticationFilter(JwtService jwtService, AuthRepository users) { this.jwtService = jwtService; this.users = users; }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")) {
            try {
                CurrentUser tokenUser = jwtService.parseAccessToken(header.substring(7));
                UserAccount account = users.findUserById(tokenUser.id()).orElseThrow();
                if (!account.active() || !"ACTIVE".equals(account.status()) || !account.role().equals(tokenUser.role()))
                    throw new IllegalArgumentException("Account is not active.");
                users.touchLastActive(account.id());
                CurrentUser user = new CurrentUser(account.id(), account.email(), account.role());
                var authentication = new UsernamePasswordAuthenticationToken(user, null,
                        List.of(new SimpleGrantedAuthority("ROLE_" + account.role())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (IllegalArgumentException ignored) {
                SecurityContextHolder.clearContext();
            }
        }
        chain.doFilter(request, response);
    }
}
