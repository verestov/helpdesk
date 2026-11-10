package com.helpdesk.HelpDesk.dto.user;

import com.helpdesk.HelpDesk.enums.Roles;
import jakarta.validation.constraints.NotNull;

public record UpdateUserRoleRequest(
        @NotNull
        Roles role
) {
}
