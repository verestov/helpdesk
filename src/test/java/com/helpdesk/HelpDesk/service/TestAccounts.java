package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.auth.UserDetailsImpl;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Roles;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

final class TestAccounts {
    private TestAccounts() {}

    static User account(String email, Roles role) {
        var user = new User();
        user.setEmail(email);
        user.setFullName(email);
        user.setPassword("unused");
        user.setRole(role);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        return user;
    }

    static void authenticate(User user) {
        var principal = UserDetailsImpl.build(user);
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
        SecurityContextHolder.setContext(context);
    }
}
