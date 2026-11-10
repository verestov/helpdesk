package com.helpdesk.HelpDesk.dto.ticket;

import com.helpdesk.HelpDesk.dto.CategoryResponse;
import com.helpdesk.HelpDesk.dto.UserResponse;
import com.helpdesk.HelpDesk.enums.Priority;
import com.helpdesk.HelpDesk.enums.TicketStatus;

public record CreateTicketResponse(
        Long id,
        String title,
        String description,
        TicketStatus status,
        Priority priority,
        UserResponse createdBy,
        UserResponse assignedTo,
        CategoryResponse category
) {
}
