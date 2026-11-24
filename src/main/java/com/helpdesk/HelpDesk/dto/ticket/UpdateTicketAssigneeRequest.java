package com.helpdesk.HelpDesk.dto.ticket;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Positive;

public record UpdateTicketAssigneeRequest(
        @JsonProperty(required = true) @Positive
        Long assigneeId
) {
}
