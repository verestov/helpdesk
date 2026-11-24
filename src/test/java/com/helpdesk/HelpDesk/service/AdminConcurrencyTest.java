package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.user.UpdateUserStatusRequest;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.exception.BusinessRuleViolationException;
import com.helpdesk.HelpDesk.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.hbm2ddl.create_namespaces=true"
})
class AdminConcurrencyTest {
    // The global admin count must be exactly three regardless of data in the development schema.
    private static final String SCHEMA = "test_admin_" + UUID.randomUUID().toString().replace("-", "");

    @DynamicPropertySource
    static void useIsolatedSchema(DynamicPropertyRegistry registry) {
        registry.add("spring.jpa.properties.hibernate.default_schema", () -> SCHEMA);
    }

    @Autowired UserRepository users;
    @Autowired UserService service;
    @Autowired PlatformTransactionManager transactions;
    @Autowired JdbcTemplate jdbc;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void overlappingDisablesLeaveAtLeastTwoActiveAdmins() {
        User actor = users.save(TestAccounts.account("actor@example.test", Roles.ADMIN));
        User first = users.save(TestAccounts.account("first@example.test", Roles.ADMIN));
        User second = users.save(TestAccounts.account("second@example.test", Roles.ADMIN));
        TestAccounts.authenticate(actor);

        var independent = new TransactionTemplate(transactions);
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        new TransactionTemplate(transactions).executeWithoutResult(firstTransaction -> {
            service.updateUserStatus(first.getId(), new UpdateUserStatusRequest(false));
            users.flush();
            // Start the second transaction while the first update is still uncommitted.
            try {
                independent.executeWithoutResult(secondTransaction -> {
                    jdbc.execute("SET LOCAL lock_timeout = '1s'");
                    service.updateUserStatus(second.getId(), new UpdateUserStatusRequest(false));
                });
            } catch (CannotAcquireLockException | BusinessRuleViolationException expectedContention) {
                // A serialized implementation can wait for the first commit or reject the second call.
            }
        });

        assertFalse(users.findById(first.getId()).orElseThrow().isEnabled());
        assertEquals(2, users.countActiveAdmins(Roles.ADMIN));
        assertTrue(users.findById(second.getId()).orElseThrow().isEnabled());
        assertThrows(BusinessRuleViolationException.class,
                () -> service.updateUserStatus(second.getId(), new UpdateUserStatusRequest(false)));
    }
}
