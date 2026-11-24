package com.helpdesk.HelpDesk.dto.ticket;

import com.helpdesk.HelpDesk.enums.Priority;
import com.helpdesk.HelpDesk.enums.TicketStatus;

public record TicketFilter(TicketStatus status, Priority priority, Long categoryId, Long assigneeId) {
    public static final TicketFilter ALL = new TicketFilter(null, null, null, null);
}
