package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.ticket.TicketFilter;
import com.helpdesk.HelpDesk.dto.ticket.TicketResponse;
import com.helpdesk.HelpDesk.entity.Category;
import com.helpdesk.HelpDesk.entity.Ticket;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Priority;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.enums.TicketStatus;
import com.helpdesk.HelpDesk.repository.CategoryRepository;
import com.helpdesk.HelpDesk.repository.TicketRepository;
import com.helpdesk.HelpDesk.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.hibernate.SessionEventListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@Transactional
class TicketVisibilityTest {
    @Autowired TicketService service;
    @Autowired UserRepository users;
    @Autowired TicketRepository tickets;
    @Autowired CategoryRepository categories;
    @Autowired EntityManager entityManager;

    private User owner;
    private Category category;
    private Long openId;
    private Long archivedId;
    private Long otherOpenId;
    private Long otherArchiveId;

    @BeforeEach
    void createTicketsForTwoCustomers() {
        owner = newUser(Roles.CUSTOMER);
        var other = newUser(Roles.CUSTOMER);
        category = new Category();
        category.setName(UUID.randomUUID().toString());
        category.setCreatedAt(LocalDateTime.now());
        categories.save(category);
        openId = createTicket(owner, TicketStatus.OPEN, false);
        archivedId = createTicket(owner, TicketStatus.CLOSED, true);
        otherOpenId = createTicket(other, TicketStatus.OPEN, false);
        otherArchiveId = createTicket(other, TicketStatus.CLOSED, true);
        entityManager.flush();
        entityManager.clear();
        TestAccounts.authenticate(owner);
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void customerListIncludesOwnOpenAndArchivedTickets() {
        assertThat(service.getAllTickets()).extracting(TicketResponse::id)
                .containsExactlyInAnyOrder(openId, archivedId);
        assertThat(service.getMyTickets(TicketFilter.ALL, Pageable.unpaged())).extracting(TicketResponse::id)
                .containsExactlyInAnyOrder(openId, archivedId);
        assertThat(service.getArchivedTickets(TicketFilter.ALL, Pageable.unpaged())).extracting(TicketResponse::id)
                .containsExactly(archivedId);
    }

    @Test
    void statusFilterReturnsOnlyOwnersClosedTicketsIncludingArchive() {
        assertThat(service.getTicketsByStatus(TicketStatus.CLOSED)).extracting(TicketResponse::id)
                .containsExactly(archivedId);
    }

    @Test
    void customerCanReadOwnArchivedTicketAndCannotReadAnotherCustomersTickets() {
        assertThat(service.getTicketById(archivedId).isArchived()).isTrue();
        assertThrows(AccessDeniedException.class, () -> service.getTicketById(otherOpenId));
        assertThrows(AccessDeniedException.class, () -> service.getTicketById(otherArchiveId));
    }

    @ParameterizedTest
    @EnumSource(value = Roles.class, names = {"ADMIN", "SUPPORT"})
    void staffQueueContainsActiveTicketsAndStaffCanReadArchive(Roles role) {
        TestAccounts.authenticate(newUser(role));
        assertThat(service.getAllTickets()).extracting(TicketResponse::id)
                .contains(openId, otherOpenId).doesNotContain(archivedId, otherArchiveId);
        assertThat(service.getTicketById(otherArchiveId).isArchived()).isTrue();
        var categoryFilter = new TicketFilter(null, null, category.getId(), null);
        var page = service.getArchivedTickets(categoryFilter, PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(TicketResponse::id).containsExactly(otherArchiveId);
        assertThat(service.getMyTickets(categoryFilter, Pageable.unpaged())).isEmpty();
    }

    @Test
    void combinedFiltersRespectOwnershipAndExcludeMismatchedFields() {
        User assignee = newUser(Roles.SUPPORT);
        tickets.findById(openId).orElseThrow().setAssignedTo(assignee);
        tickets.findById(otherOpenId).orElseThrow().setAssignedTo(assignee);
        var addedId = createTicket(owner, TicketStatus.OPEN, false);
        var changed = tickets.findById(addedId).orElseThrow();
        changed.setAssignedTo(assignee);
        changed.setPriority(Priority.HIGH);
        createTicket(owner, TicketStatus.OPEN, false);
        var differentStatus = createTicket(owner, TicketStatus.IN_PROGRESS, false);
        tickets.findById(differentStatus).orElseThrow().setAssignedTo(assignee);
        var differentCategory = createTicket(owner, TicketStatus.OPEN, false);
        var secondCategory = new Category();
        secondCategory.setName(UUID.randomUUID().toString());
        secondCategory.setCreatedAt(LocalDateTime.now());
        var changedCategory = tickets.findById(differentCategory).orElseThrow();
        changedCategory.setCategory(categories.save(secondCategory));
        changedCategory.setAssignedTo(assignee);
        entityManager.flush();
        entityManager.clear();

        var filter = new TicketFilter(TicketStatus.OPEN, Priority.MEDIUM, category.getId(), assignee.getId());
        assertThat(service.getTickets(filter, PageRequest.of(0, 5))).extracting(TicketResponse::id)
                .containsExactly(openId);
    }

    @Test
    void equalCreationDatesHaveStablePagesAndPageMappingAvoidsNPlusOne() {
        var support = newUser(Roles.SUPPORT);
        var extraId = createTicket(newUser(Roles.CUSTOMER), TicketStatus.OPEN, false);
        var sameTime = LocalDateTime.of(2025, 3, 4, 10, 0);
        for (Long id : java.util.List.of(openId, otherOpenId, extraId)) {
            var ticket = tickets.findById(id).orElseThrow();
            ticket.setAssignedTo(newUser(Roles.SUPPORT));
            ticket.setCreatedAt(sameTime);
        }
        entityManager.flush();
        entityManager.clear();
        TestAccounts.authenticate(support);
        var statements = new AtomicInteger();
        entityManager.unwrap(Session.class).addEventListeners(new SessionEventListener() {
            @Override
            public void jdbcPrepareStatementStart() {
                statements.incrementAndGet();
            }
        });
        var filter = new TicketFilter(null, null, category.getId(), null);
        var first = service.getTickets(filter, PageRequest.of(0, 2));
        assertThat(first.getTotalElements()).isEqualTo(3);
        assertThat(first.getContent()).extracting(TicketResponse::id).containsExactly(extraId, otherOpenId);
        // One account lookup, one page with to-one joins, one count.
        assertThat(statements.get()).isBetween(1, 3);
        assertThat(service.getTickets(filter, PageRequest.of(1, 2))).extracting(TicketResponse::id)
                .containsExactly(openId);
    }

    private User newUser(Roles role) {
        return users.save(TestAccounts.account(UUID.randomUUID() + "@example.test", role));
    }

    private Long createTicket(User creator, TicketStatus status, boolean archived) {
        var ticket = new Ticket();
        ticket.setTitle("Printer");
        ticket.setDescription("Printer offline");
        ticket.setCreatedBy(creator);
        ticket.setCategory(category);
        ticket.setPriority(Priority.MEDIUM);
        ticket.setStatus(status);
        ticket.setArchived(archived);
        ticket.setCreatedAt(LocalDateTime.now().minusMonths(2));
        ticket.setUpdatedAt(ticket.getCreatedAt());
        return tickets.save(ticket).getId();
    }
}
