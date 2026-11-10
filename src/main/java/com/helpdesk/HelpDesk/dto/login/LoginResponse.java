package com.helpdesk.HelpDesk.dto.login;

import com.helpdesk.HelpDesk.dto.user.UserResponse;

public record LoginResponse(
        String token,
        UserResponse user
) {
}
