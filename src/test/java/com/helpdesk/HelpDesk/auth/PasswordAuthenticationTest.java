package com.helpdesk.HelpDesk.auth;

import com.helpdesk.HelpDesk.config.SecurityConfig;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PasswordAuthenticationTest {
    private static final String EMAIL = "support@example.test";
    private static final String PASSWORD = "test-password";

    private final UserRepository users = mock(UserRepository.class);
    private final SecurityConfig config = new SecurityConfig();
    private final PasswordEncoder encoder = config.passwordEncoder();
    private final AuthenticationManager manager = config.authenticationManager(new UserDetailsServiceImpl(users), encoder);

    @Test
    void authenticatesActiveUserWithEmailAndSupportRole() {
        givenAccount(true);

        var result = authenticate(PASSWORD);

        assertTrue(result.isAuthenticated());
        assertEquals(EMAIL, result.getName());
        assertEquals(42L, assertInstanceOf(UserDetailsImpl.class, result.getPrincipal()).getId());
        assertTrue(result.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_SUPPORT")));
    }

    @Test
    void rejectsWrongPassword() {
        givenAccount(true);
        assertThrows(BadCredentialsException.class, () -> authenticate("incorrect"));
    }

    @Test
    void rejectsDisabledAccountEvenWithCorrectPassword() {
        givenAccount(false);
        assertThrows(DisabledException.class, () -> authenticate(PASSWORD));
    }

    @Test
    void rejectsUnknownEmail() {
        when(users.findByEmail(EMAIL)).thenReturn(Optional.empty());
        assertThrows(BadCredentialsException.class, () -> authenticate(PASSWORD));
    }

    private Authentication authenticate(String password) {
        return manager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, password));
    }

    private void givenAccount(boolean enabled) {
        var user = new User();
        user.setId(42L);
        user.setEmail(EMAIL);
        user.setPassword(encoder.encode(PASSWORD));
        user.setRole(Roles.SUPPORT);
        user.setEnabled(enabled);
        when(users.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }
}
