package com.helpdesk.HelpDesk.dto.ticket;

import com.helpdesk.HelpDesk.dto.category.CategoryResponse;
import com.helpdesk.HelpDesk.dto.user.UserResponse;
import com.helpdesk.HelpDesk.enums.Priority;
import com.helpdesk.HelpDesk.enums.TicketStatus;

import java.time.LocalDateTime;

public record TicketResponse(
        Long id,
        String title,
        String description,
        TicketStatus status,
        Priority priority,
        UserResponse createdBy,
        UserResponse assignedTo,
        CategoryResponse category,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        boolean isArchived
) {
}
