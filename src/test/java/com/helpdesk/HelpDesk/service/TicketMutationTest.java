package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.ticket.CreateTicketRequest;
import com.helpdesk.HelpDesk.dto.ticket.UpdateTicketAssigneeRequest;
import com.helpdesk.HelpDesk.dto.ticket.UpdateTicketPriorityRequest;
import com.helpdesk.HelpDesk.dto.ticket.UpdateTicketStatusRequest;
import com.helpdesk.HelpDesk.entity.Category;
import com.helpdesk.HelpDesk.entity.Ticket;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Priority;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.enums.TicketStatus;
import com.helpdesk.HelpDesk.mapper.TicketMapper;
import com.helpdesk.HelpDesk.repository.CategoryRepository;
import com.helpdesk.HelpDesk.repository.TicketRepository;
import com.helpdesk.HelpDesk.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

@SpringBootTest
class TicketMutationTest {
    @Autowired TicketService service;
    @Autowired TicketRepository tickets;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    @MockitoSpyBean TicketMapper mapper;

    private User owner;
    private User support;
    private User admin;
    private Long categoryId;
    private Long ticketId;

    @BeforeEach
    void createTicket() {
        owner = newUser(Roles.CUSTOMER);
        support = newUser(Roles.SUPPORT);
        admin = newUser(Roles.ADMIN);
        var category = new Category();
        category.setName(UUID.randomUUID().toString());
        category.setCreatedAt(LocalDateTime.now());
        categoryId = categories.save(category).getId();
        TestAccounts.authenticate(owner);
        ticketId = service.createTicket(new CreateTicketRequest("VPN", "No connection", Priority.LOW, categoryId)).id();
        TestAccounts.authenticate(support);
    }

    @AfterEach
    void removeFixture() {
        SecurityContextHolder.clearContext();
        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            jdbc.update("delete from helpdesk.ticket_histories where ticket_id = ?", ticketId);
            tickets.deleteById(ticketId);
            tickets.flush();
            categories.deleteById(categoryId);
            users.deleteAllById(List.of(owner.getId(), support.getId(), admin.getId()));
        });
    }

    @Test
    void selfAssignmentReassignmentAndRemovalPersistWithActorAndValues() {
        Long supportId = support.getId();
        Long adminId = admin.getId();
        service.updateTicketAssignee(ticketId, new UpdateTicketAssigneeRequest(supportId));
        service.updateTicketAssignee(ticketId, new UpdateTicketAssigneeRequest(adminId));
        TestAccounts.authenticate(admin);
        var result = service.updateTicketAssignee(ticketId, new UpdateTicketAssigneeRequest(null));

        assertThat(result.status()).isEqualTo(TicketStatus.OPEN);
        assertThat(service.getTicketById(ticketId).assignedTo()).isNull();
        assertThat(history()).containsExactly(
                new Change("assignedTo", null, supportId.toString(), supportId),
                new Change("assignedTo", supportId.toString(), adminId.toString(), supportId),
                new Change("assignedTo", adminId.toString(), null, adminId));
        var lastEventAt = jdbc.queryForObject(
                "select max(created_at) from helpdesk.ticket_histories where ticket_id = ?", LocalDateTime.class, ticketId);
        assertThat(tickets.findById(ticketId).orElseThrow().getUpdatedAt()).isEqualTo(lastEventAt);
    }

    @Test
    void failureAfterHistoryWriteRollsBackTicketAndHistory() {
        var before = tickets.findById(ticketId).orElseThrow().getUpdatedAt();
        doThrow(new IllegalStateException("Simulated response mapping failure")).when(mapper).toDTO(any(Ticket.class));

        assertThrows(IllegalStateException.class,
                () -> service.updateTicketPriority(ticketId, new UpdateTicketPriorityRequest(Priority.HIGH)));

        var persisted = tickets.findById(ticketId).orElseThrow();
        assertThat(persisted.getPriority()).isEqualTo(Priority.LOW);
        assertThat(persisted.getUpdatedAt()).isEqualTo(before);
        assertThat(history()).isEmpty();
    }

    @Test
    void overlappingUpdatesAreSerializedBeforeCheckingTheTransition() {
        var competing = new TransactionTemplate(transactions);
        competing.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);

        new TransactionTemplate(transactions).executeWithoutResult(tx -> {
            service.updateTicketStatus(ticketId, new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS));
            // A second connection overlaps the first uncommitted write and cannot validate against stale OPEN.
            assertThrows(PessimisticLockingFailureException.class, () -> competing.executeWithoutResult(other -> {
                jdbc.execute("SET LOCAL lock_timeout = '500ms'");
                service.updateTicketStatus(ticketId, new UpdateTicketStatusRequest(TicketStatus.RESOLVED));
            }));
        });
        var afterCommit = service.updateTicketStatus(ticketId, new UpdateTicketStatusRequest(TicketStatus.RESOLVED));
        assertThat(afterCommit.status()).isEqualTo(TicketStatus.RESOLVED);
        assertThat(history()).containsExactly(
                new Change("status", "OPEN", "IN_PROGRESS", support.getId()),
                new Change("status", "IN_PROGRESS", "RESOLVED", support.getId()));
    }

    private record Change(String field, String oldValue, String newValue, Long actorId) {}

    private List<Change> history() {
        return jdbc.query("""
                select field_name, old_value, new_value, actor_id
                from helpdesk.ticket_histories where ticket_id = ? order by id
                """, (row, index) -> new Change(row.getString("field_name"), row.getString("old_value"),
                row.getString("new_value"), row.getLong("actor_id")), ticketId);
    }

    private User newUser(Roles role) {
        return users.save(TestAccounts.account(UUID.randomUUID() + "@example.test", role));
    }
}
