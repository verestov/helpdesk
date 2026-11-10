package com.helpdesk.HelpDesk.dto;

import com.helpdesk.HelpDesk.enums.Roles;

public record UserResponse(
        Long id,
        String email,
        String fullName,
        Roles role
) {
}
