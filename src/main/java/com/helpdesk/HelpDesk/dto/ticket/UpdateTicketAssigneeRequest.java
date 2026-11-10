package com.helpdesk.HelpDesk.dto.ticket;

import jakarta.validation.constraints.Positive;

public record UpdateTicketAssigneeRequest(
        @Positive
        Long assigneeId
) {
}
