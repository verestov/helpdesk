package com.helpdesk.HelpDesk.auth;

import com.helpdesk.HelpDesk.config.SecurityConfig;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig(JwtSecurityTest.TestConfig.class)
@TestPropertySource(properties = {
        "JWT_SECRET=" + JwtSecurityTest.TEST_SECRET,
        "JWT_EXPIRATION=900000",
        "JWT_REFRESH_EXPIRATION=1800000"
})
class JwtSecurityTest {
    static final String TEST_SECRET = "local-test-only-key-for-jwt-security-tests";
    @TestConfiguration
    @EnableWebMvc
    @EnableWebSecurity
    @Import({SecurityConfig.class, JwtCore.class, UserDetailsServiceImpl.class, ProbeController.class})
    static class TestConfig {}

    @TestComponent
    @RestController
    static class ProbeController {
        @PostMapping("/api/v1/auth/login")
        String publicRoute() {
            return "public";
        }

        @GetMapping("/test/me")
        String currentUser() {
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            return ((UserDetailsImpl) authentication.getPrincipal()).getId().toString();
        }
    }

    @MockitoBean UserRepository users;
    @Autowired JwtCore jwtCore;
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    private MockMvc mvc;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void issuesSignedTokenForFifteenMinutes() {
        var principal = UserDetailsImpl.build(account(true));
        var authentication = UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities());
        var token = jwtCore.generateToken(authentication);
        var claims = Jwts.parser().verifyWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8)))
                .build().parseSignedClaims(token).getPayload();
        assertEquals("42", claims.getSubject());
        assertEquals(900_000L, claims.getExpiration().getTime() - claims.getIssuedAt().getTime(), 1000L);
    }

    @Test
    void loginRouteIsPublic() throws Exception {
        mvc.perform(post("/api/v1/auth/login")).andExpect(status().isOk());
    }

    @Test
    void protectedRouteRequiresAuthentication() throws Exception {
        mvc.perform(get("/test/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void validTokenIdentifiesActiveUser() throws Exception {
        account(true);
        mvc.perform(get("/test/me").header("Authorization", "Bearer " + tokenExpiringIn(300)))
                .andExpect(status().isOk()).andExpect(content().string("42"));
    }

    @Test
    void disabledAccountCannotUseAnUnexpiredToken() throws Exception {
        var token = tokenExpiringIn(300);
        account(false);
        mvc.perform(get("/test/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredTokenIsRejected() throws Exception {
        mvc.perform(get("/test/me").header("Authorization", "Bearer " + tokenExpiringIn(-60)))
                .andExpect(status().isUnauthorized());
    }

    private String tokenExpiringIn(long seconds) {
        return Jwts.builder().subject("42").expiration(Date.from(Instant.now().plusSeconds(seconds)))
                .signWith(Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8))).compact();
    }

    private @NonNull User account(boolean enabled) {
        var user = new User();
        user.setId(42L);
        user.setEmail("support@example.test");
        user.setRole(Roles.SUPPORT);
        user.setEnabled(enabled);
        when(users.findById(42L)).thenReturn(Optional.of(user));
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        return user;
    }
}
