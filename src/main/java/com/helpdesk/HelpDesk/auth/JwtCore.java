package com.helpdesk.HelpDesk.auth;

import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
public class JwtCore {
    private final SecretKey signingKey;
    private final JwtParser parser;
    private final long lifetimeMillis;

    public JwtCore(@Value("${JWT_SECRET}") String secret,
                   @Value("${JWT_EXPIRATION}") long lifetimeMillis) {
        if (lifetimeMillis <= 0) {
            throw new IllegalArgumentException("JWT_EXPIRATION must be positive (milliseconds)");
        }
        this.lifetimeMillis = lifetimeMillis;
        signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        parser = Jwts.parser().verifyWith(signingKey).build();
    }

    public String generateToken(@NonNull Authentication authentication) {
        if (!authentication.isAuthenticated() || !(authentication.getPrincipal() instanceof UserDetailsImpl user)) {
            throw new IllegalArgumentException("An authenticated HelpDesk user is required");
        }
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(user.getId().toString())
                .claim("role", user.getRole().name())
                .issuedAt(new Date(now))
                .expiration(new Date(Math.addExact(now, lifetimeMillis)))
                .signWith(signingKey)
                .compact();
    }

    public Long getIdFromToken(String token) {
        var claims = parser.parseSignedClaims(token).getPayload();
        if (claims.getExpiration() == null) {
            throw new IllegalArgumentException("Missing token expiration");
        }
        return Long.valueOf(claims.getSubject());
    }
}
