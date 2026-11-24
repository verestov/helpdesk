package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.user.UpdateUserRoleRequest;
import com.helpdesk.HelpDesk.dto.user.UpdateUserStatusRequest;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.exception.BusinessRuleViolationException;
import com.helpdesk.HelpDesk.mapper.UserMapper;
import com.helpdesk.HelpDesk.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static com.helpdesk.HelpDesk.service.TestAccounts.authenticate;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

class UserServiceTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserService service = new UserService(users, new UserMapper());

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @ParameterizedTest
    @EnumSource(value = Roles.class, names = {"CUSTOMER", "SUPPORT"})
    void existingAdminCannotBeDemoted(Roles newRole) {
        authenticate(createUser(1L, Roles.ADMIN));
        var admin = createUser(2L, Roles.ADMIN);
        assertThrows(BusinessRuleViolationException.class,
                () -> service.updateUserRole(admin.getId(), new UpdateUserRoleRequest(newRole)));
        assertEquals(Roles.ADMIN, admin.getRole());
        verify(users, never()).save(any());
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 2})
    void refusesToDisableAnAdminAtOrBelowMinimum(long count) {
        // При одном активном ADMIN инициатор и цель совпадают.
        var admin = createUser(1L, Roles.ADMIN);
        authenticate(count == 1 ? admin : createUser(2L, Roles.ADMIN));
        when(users.countActiveAdmins(Roles.ADMIN)).thenReturn(count);
        assertDisableRejected(admin);
    }

    @Test
    void adminCannotDisableSelfEvenWhenThreeAdminsAreActive() {
        var admin = createUser(1L, Roles.ADMIN);
        authenticate(admin);
        when(users.countActiveAdmins(Roles.ADMIN)).thenReturn(3L);
        assertDisableRejected(admin);
    }

    @ParameterizedTest
    @EnumSource(value = Roles.class, names = {"CUSTOMER", "SUPPORT"})
    void onlyAnotherAdminCanDisableAnAdmin(Roles actorRole) {
        authenticate(createUser(1L, actorRole));
        var admin = createUser(2L, Roles.ADMIN);
        when(users.countActiveAdmins(Roles.ADMIN)).thenReturn(3L);
        assertDisableRejected(admin);
    }

    @Test
    void disablingAlreadyDisabledAdminIsIdempotentAtMinimum() {
        authenticate(createUser(1L, Roles.ADMIN));
        var admin = createUser(2L, Roles.ADMIN);
        admin.setEnabled(false);
        when(users.countActiveAdmins(Roles.ADMIN)).thenReturn(2L);
        service.updateUserStatus(admin.getId(), new UpdateUserStatusRequest(false));
        assertFalse(admin.isEnabled());
    }

    @Test
    void anotherAdminCanDisableOneOfThreeActiveAdmins() {
        authenticate(createUser(1L, Roles.ADMIN));
        var admin = createUser(2L, Roles.ADMIN);
        when(users.countActiveAdmins(Roles.ADMIN)).thenReturn(3L);
        service.updateUserStatus(admin.getId(), new UpdateUserStatusRequest(false));
        assertFalse(admin.isEnabled());
    }

    private User createUser(Long id, Roles role) {
        var user = TestAccounts.account("user" + id + "@example.test", role);
        user.setId(id);
        when(users.findById(id)).thenReturn(Optional.of(user));
        when(users.save(user)).thenAnswer(returnsFirstArg());
        return user;
    }

    private void assertDisableRejected(User target) {
        assertThatThrownBy(() -> service.updateUserStatus(target.getId(), new UpdateUserStatusRequest(false)))
                .isInstanceOfAny(BusinessRuleViolationException.class, AccessDeniedException.class);
        assertTrue(target.isEnabled());
        verify(users, never()).save(any());
    }
}
