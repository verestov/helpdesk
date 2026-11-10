package com.helpdesk.HelpDesk.dto.user;

import com.helpdesk.HelpDesk.enums.Roles;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        Roles role
) {
}
