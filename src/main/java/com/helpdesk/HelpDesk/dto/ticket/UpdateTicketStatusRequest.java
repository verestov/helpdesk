package com.helpdesk.HelpDesk.dto.ticket;

import com.helpdesk.HelpDesk.enums.TicketStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateTicketStatusRequest(
        @NotNull
        TicketStatus status
) {
}
