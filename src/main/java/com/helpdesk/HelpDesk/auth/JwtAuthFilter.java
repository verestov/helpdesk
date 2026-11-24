package com.helpdesk.HelpDesk.auth;

import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtCore jwtCore;
    private final UserRepository userRepository;

    public JwtAuthFilter(JwtCore jwtCore, UserRepository userRepository) {
        this.jwtCore = jwtCore;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            filterChain.doFilter(request, response);
            return;
        }

        Long userId;
        try {
            userId = jwtCore.getIdFromToken(authHeader.substring(7));
        } catch (JwtException | IllegalArgumentException ex) {
            unauthorized(response);
            return;
        }

        var user = userRepository.findById(userId).filter(User::isEnabled);
        if (user.isEmpty()) {
            unauthorized(response);
            return;
        }

        var principal = UserDetailsImpl.build(user.get());
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        filterChain.doFilter(request, response);
    }

    private void unauthorized(HttpServletResponse response) {
        response.setHeader("WWW-Authenticate", "Bearer");
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    }
}
