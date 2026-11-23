package com.helpdesk.HelpDesk.mapper;

import com.helpdesk.HelpDesk.entity.Category;
import com.helpdesk.HelpDesk.entity.Ticket;
import com.helpdesk.HelpDesk.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class TicketMapperTest {
    private final TicketMapper mapper = new TicketMapper(new UserMapper(), new CategoryMapper());

    @Test
    void shouldPreserveCreatorAndCategoryWithoutAssignee() {
        var ticket = createTicket();

        var response = mapper.toDTO(ticket);

        assertNull(response.assignedTo());
        assertEquals(1L, response.createdBy().id());
        assertEquals(2L, response.category().id());
        assertEquals("Technical support", response.category().name());
    }

    @Test
    void shouldMapAssigneeAndCategoryIndependently() {
        var ticket = createTicket();
        var assignee = new User();
        assignee.setId(3L);
        ticket.setAssignedTo(assignee);

        var response = mapper.toDTO(ticket);

        assertEquals(3L, response.assignedTo().id());
        assertEquals(1L, response.createdBy().id());
        assertEquals(2L, response.category().id());
        assertEquals("Technical support", response.category().name());
    }

    private Ticket createTicket() {
        var creator = new User();
        creator.setId(1L);
        var category = new Category();
        category.setId(2L);
        category.setName("Technical support");
        var ticket = new Ticket();
        ticket.setCreatedBy(creator);
        ticket.setCategory(category);
        return ticket;
    }
}
