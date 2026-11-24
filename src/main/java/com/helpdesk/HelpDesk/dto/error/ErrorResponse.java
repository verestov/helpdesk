package com.helpdesk.HelpDesk.dto.error;

import java.time.LocalDateTime;

public record ErrorResponse(
        String message,
        String detailedMessage,
        LocalDateTime errorTime
) {
}
