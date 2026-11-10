package com.helpdesk.HelpDesk.dto.login;

import com.helpdesk.HelpDesk.dto.UserResponse;

public record LoginResponse(
        String token,
        UserResponse user
) {
}
