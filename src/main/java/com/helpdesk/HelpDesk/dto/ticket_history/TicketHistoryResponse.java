package com.helpdesk.HelpDesk.dto.ticket_history;

import com.helpdesk.HelpDesk.dto.user.UserResponse;

import java.time.LocalDateTime;

public record TicketHistoryResponse(
        Long ticketId,
        UserResponse actor,
        String fieldName,
        String oldValue,
        String newValue,
        LocalDateTime createdAt
) {
}
