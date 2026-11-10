package com.helpdesk.HelpDesk.dto.user;

import jakarta.validation.constraints.NotNull;

public record UpdateUserStatusRequest(
        @NotNull
        Boolean isEnabled
) {
}
