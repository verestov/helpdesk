package com.helpdesk.HelpDesk.mapper;

import com.helpdesk.HelpDesk.dto.ticket.TicketResponse;
import com.helpdesk.HelpDesk.entity.Ticket;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class TicketMapper implements GenericMapper<Ticket, TicketResponse> {
    private final UserMapper userMapper;
    private final CategoryMapper categoryMapper;
    public TicketMapper(
            UserMapper userMapper,
            CategoryMapper categoryMapper
    ) {
        this.userMapper = userMapper;
        this.categoryMapper = categoryMapper;
    }
    @Override
    public TicketResponse toDTO(@NonNull Ticket ticket) {
        return new TicketResponse(
                ticket.getId(),
                ticket.getTitle(),
                ticket.getDescription(),
                ticket.getStatus(),
                ticket.getPriority(),
                userMapper.toDTO(ticket.getCreatedBy()),
                ticket.getAssignedTo() == null
                        ? null
                        : userMapper.toDTO(ticket.getAssignedTo()),
                categoryMapper.toDTO(ticket.getCategory()),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.isArchived()
        );
    }
}
