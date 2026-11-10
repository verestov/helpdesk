package com.helpdesk.HelpDesk.dto.ticket;

import com.helpdesk.HelpDesk.enums.Priority;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketPriorityRequest(
        @NotNull
        Priority priority
) {
}
