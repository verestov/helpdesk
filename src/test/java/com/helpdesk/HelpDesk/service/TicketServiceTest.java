package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.ticket.*;
import com.helpdesk.HelpDesk.entity.*;
import com.helpdesk.HelpDesk.enums.Priority;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.enums.TicketStatus;
import com.helpdesk.HelpDesk.exception.BusinessRuleViolationException;
import com.helpdesk.HelpDesk.exception.ResourceNotFoundException;
import com.helpdesk.HelpDesk.mapper.CategoryMapper;
import com.helpdesk.HelpDesk.mapper.TicketMapper;
import com.helpdesk.HelpDesk.mapper.UserMapper;
import com.helpdesk.HelpDesk.repository.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.mockito.ArgumentCaptor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static com.helpdesk.HelpDesk.enums.TicketStatus.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.AdditionalAnswers.returnsFirstArg;

class TicketServiceTest {
    private static final LocalDateTime CREATED = LocalDateTime.of(2024, 1, 31, 10, 30);
    private static final LocalDateTime ARCHIVE_BOUNDARY = LocalDateTime.of(2024, 2, 29, 10, 30);
    private final TicketRepository tickets = mock(TicketRepository.class);
    private final CategoryRepository categories = mock(CategoryRepository.class);
    private final TicketHistoryRepository history = mock(TicketHistoryRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final TicketService service = new TicketService(tickets,
            new TicketMapper(new UserMapper(), new CategoryMapper()), categories, history, users);
    private final Ticket ticket = new Ticket();

    @BeforeEach
    void prepareTicket() {
        var owner = TestAccounts.account("owner@example.test", Roles.CUSTOMER);
        owner.setId(2L);
        ticket.setId(10L);
        ticket.setCreatedBy(owner);
        ticket.setCategory(new Category());
        ticket.setStatus(RESOLVED);
        ticket.setPriority(Priority.LOW);
        ticket.setCreatedAt(CREATED);
        ticket.setUpdatedAt(CREATED.plusDays(3));
        when(tickets.findByIdForUpdate(ticket.getId())).thenReturn(Optional.of(ticket));
        when(tickets.save(any(Ticket.class))).thenAnswer(returnsFirstArg());
    }

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    private User signIn(Roles role) {
        var user = TestAccounts.account("actor@example.test", role);
        user.setId(1L);
        when(users.findById(user.getId())).thenReturn(Optional.of(user));
        TestAccounts.authenticate(user);
        return user;
    }

    private static final Map<TicketStatus, Set<TicketStatus>> ALLOWED = Map.of(
            OPEN, Set.of(IN_PROGRESS),
            IN_PROGRESS, Set.of(WAITING_FOR_CUSTOMER, RESOLVED),
            WAITING_FOR_CUSTOMER, Set.of(IN_PROGRESS, RESOLVED),
            RESOLVED, Set.of(CLOSED),
            CLOSED, Set.of());

    static Stream<Arguments> transitions() {
        return Arrays.stream(TicketStatus.values()).flatMap(from ->
                Arrays.stream(TicketStatus.values()).map(to -> Arguments.of(from, to)));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @MethodSource("transitions")
    void enforcesStatusTransitions(TicketStatus from, TicketStatus to) {
        var actor = signIn(Roles.SUPPORT);
        ticket.setStatus(from);
        var before = ticket.getUpdatedAt();
        var request = new UpdateTicketStatusRequest(to);
        if (ALLOWED.get(from).contains(to)) {
            var result = service.updateTicketStatus(ticket.getId(), request);
            assertEquals(to, result.status());
            assertTrue(ticket.getUpdatedAt().isAfter(before));
            verify(tickets).save(ticket);
            assertHistory(actor, "status", from.name(), to.name());
        } else {
            assertThrows(BusinessRuleViolationException.class,
                    () -> service.updateTicketStatus(ticket.getId(), request));
            assertEquals(from, ticket.getStatus());
            assertUnchanged(before);
        }
    }

    @Test
    void createsOpenTicketForAuthenticatedCustomer() {
        var actor = signIn(Roles.CUSTOMER);
        var category = new Category();
        category.setId(17L);
        when(categories.findById(17L)).thenReturn(Optional.of(category));
        var result = service.createTicket(new CreateTicketRequest("VPN", "Connection lost", Priority.HIGH, 17L));
        assertEquals(actor.getId(), result.createdBy().id());
        assertEquals(OPEN, result.status());
        assertEquals(Priority.HIGH, result.priority());
        assertEquals(17L, result.category().id());
        assertNull(result.assignedTo());
        assertFalse(result.isArchived());
        assertNotNull(result.createdAt());
        assertFalse(result.updatedAt().isBefore(result.createdAt()));
    }

    @Test
    void cannotCreateWithMissingCategory() {
        signIn(Roles.CUSTOMER);
        assertThrows(ResourceNotFoundException.class, () -> service.createTicket(
                new CreateTicketRequest("VPN", "Connection lost", Priority.HIGH, 17L)));
        verify(tickets, never()).save(any());
    }

    @Test
    void missingAuthenticationOrDisabledAccountCannotReadTickets() {
        SecurityContextHolder.clearContext();
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::getAllTickets);
        signIn(Roles.ADMIN).setEnabled(false);
        assertThrows(AuthenticationCredentialsNotFoundException.class, service::getAllTickets);
    }

    @ParameterizedTest
    @EnumSource(value = Roles.class, names = {"SUPPORT", "ADMIN"})
    void ticketCreationIsReservedForCustomers(Roles role) {
        signIn(role);
        assertThrows(AccessDeniedException.class, () -> service.createTicket(
                new CreateTicketRequest("VPN", "Connection lost", Priority.HIGH, 17L)));
        verify(tickets, never()).save(any());
    }

    @Test
    void customerCannotChangeStatusOfOwnTicket() {
        ticket.setCreatedBy(signIn(Roles.CUSTOMER));
        var before = ticket.getUpdatedAt();
        assertThrows(AccessDeniedException.class,
                () -> service.updateTicketStatus(ticket.getId(), new UpdateTicketStatusRequest(CLOSED)));
        assertEquals(RESOLVED, ticket.getStatus());
        assertUnchanged(before);
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"RESOLVED", "CLOSED"})
    void adminCanArchiveAtCalendarMonthBoundary(TicketStatus status) {
        var actor = signIn(Roles.ADMIN);
        ticket.setStatus(status);
        var comment = new Comment();
        var oldEvent = new TicketHistory();
        ticket.setComments(List.of(comment));
        ticket.setTicketHistories(List.of(oldEvent));
        try (var clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(ARCHIVE_BOUNDARY);
            var result = service.archiveTicket(new ArchiveTicketRequest(ticket.getId()));
            assertTrue(result.isArchived());
            assertEquals(status, result.status());
            assertEquals(ARCHIVE_BOUNDARY, result.updatedAt());
            assertEquals(List.of(comment), ticket.getComments());
            assertTrue(ticket.getTicketHistories().contains(oldEvent));
            verify(tickets).save(ticket);
            assertHistory(actor, "archived", "false", "true");
        }
    }

    @Test
    void refusesArchiveImmediatelyBeforeCalendarMonthBoundary() {
        signIn(Roles.ADMIN);
        var justBefore = ARCHIVE_BOUNDARY.minusNanos(1);
        try (var clock = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS)) {
            clock.when(LocalDateTime::now).thenReturn(justBefore);
            assertArchiveRejected(BusinessRuleViolationException.class);
        }
    }

    @ParameterizedTest
    @EnumSource(value = TicketStatus.class, names = {"OPEN", "IN_PROGRESS", "WAITING_FOR_CUSTOMER"})
    void refusesArchiveOfUnfinishedTicket(TicketStatus status) {
        signIn(Roles.ADMIN);
        ticket.setStatus(status);
        assertArchiveRejected(BusinessRuleViolationException.class);
    }

    @Test
    void refusesRepeatedArchive() {
        signIn(Roles.ADMIN);
        ticket.setArchived(true);
        assertArchiveRejected(BusinessRuleViolationException.class);
    }

    @ParameterizedTest
    @EnumSource(value = Roles.class, names = {"CUSTOMER", "SUPPORT"})
    void onlyAdminCanArchiveEligibleTicket(Roles role) {
        signIn(role);
        assertArchiveRejected(AccessDeniedException.class);
    }

    @Test
    void archivedResolvedTicketCannotBeClosed() {
        signIn(Roles.ADMIN);
        ticket.setArchived(true);
        var before = ticket.getUpdatedAt();
        assertThrows(BusinessRuleViolationException.class,
                () -> service.updateTicketStatus(ticket.getId(), new UpdateTicketStatusRequest(CLOSED)));
        assertEquals(RESOLVED, ticket.getStatus());
        assertUnchanged(before);
    }

    @ParameterizedTest
    @CsvSource({"CUSTOMER, false", "SUPPORT, true", "ADMIN, true"})
    void rejectsPriorityChangeForCustomerOrArchivedTicket(Roles role, boolean archived) {
        var actor = signIn(role);
        if (role == Roles.CUSTOMER) {
            ticket.setCreatedBy(actor);
        }
        ticket.setArchived(archived);
        var before = ticket.getUpdatedAt();
        var expected = archived ? BusinessRuleViolationException.class : AccessDeniedException.class;
        assertThrows(expected,
                () -> service.updateTicketPriority(ticket.getId(), new UpdateTicketPriorityRequest(Priority.HIGH)));
        assertEquals(Priority.LOW, ticket.getPriority());
        assertUnchanged(before);
    }

    @Test
    void recordsStaffPriorityChange() {
        var actor = signIn(Roles.SUPPORT);
        var before = ticket.getUpdatedAt();
        var result = service.updateTicketPriority(ticket.getId(), new UpdateTicketPriorityRequest(Priority.HIGH));
        assertAll(
                () -> assertEquals(Priority.HIGH, result.priority()),
                () -> assertTrue(result.updatedAt().isAfter(before), "updatedAt must advance"),
                () -> assertHistory(actor, "priority", "LOW", "HIGH"));
    }

    @Test
    void repeatingTheCurrentPriorityDoesNotWriteHistoryOrTouchTicket() {
        signIn(Roles.ADMIN);
        var before = ticket.getUpdatedAt();
        var result = service.updateTicketPriority(ticket.getId(), new UpdateTicketPriorityRequest(Priority.LOW));
        assertEquals(Priority.LOW, result.priority());
        assertUnchanged(before);
    }

    @ParameterizedTest
    @CsvSource({"CUSTOMER,true", "SUPPORT,false", "ADMIN,false"})
    void rejectsIneligibleAssignee(Roles role, boolean enabled) {
        signIn(Roles.SUPPORT);
        var candidate = TestAccounts.account("candidate@example.test", role);
        candidate.setEnabled(enabled);
        candidate.setId(8L);
        when(users.findById(8L)).thenReturn(Optional.of(candidate));
        assertAssignmentRejected(BusinessRuleViolationException.class, 8L);
    }

    @Test
    void rejectsMissingAssignee() {
        signIn(Roles.ADMIN);
        assertAssignmentRejected(ResourceNotFoundException.class, 8L);
    }

    @Test
    void customerCannotUnassignTheirOwnTicket() {
        ticket.setCreatedBy(signIn(Roles.CUSTOMER));
        ticket.setAssignedTo(TestAccounts.account("agent@example.test", Roles.SUPPORT));
        assertAssignmentRejected(AccessDeniedException.class, null);
    }

    @ParameterizedTest
    @CsvSource({"CLOSED,false", "RESOLVED,true"})
    void cannotAssignOrUnassignAfterClosureOrArchiving(TicketStatus status, boolean archived) {
        var actor = signIn(Roles.SUPPORT);
        ticket.setStatus(status);
        ticket.setArchived(archived);
        assertAssignmentRejected(BusinessRuleViolationException.class, actor.getId());
        ticket.setAssignedTo(actor);
        assertAssignmentRejected(BusinessRuleViolationException.class, null);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void repeatingCurrentAssignmentDoesNotWriteHistoryOrTouchTicket(boolean assigned) {
        var actor = signIn(Roles.SUPPORT);
        ticket.setAssignedTo(assigned ? actor : null);
        var before = ticket.getUpdatedAt();
        service.updateTicketAssignee(ticket.getId(), new UpdateTicketAssigneeRequest(assigned ? actor.getId() : null));
        assertUnchanged(before);
    }

    private void assertAssignmentRejected(Class<? extends RuntimeException> expected, Long assigneeId) {
        var before = ticket.getUpdatedAt();
        var oldAssignee = ticket.getAssignedTo();
        assertThrows(expected, () -> service.updateTicketAssignee(
                ticket.getId(), new UpdateTicketAssigneeRequest(assigneeId)));
        assertSame(oldAssignee, ticket.getAssignedTo());
        assertUnchanged(before);
    }

    private void assertArchiveRejected(Class<? extends RuntimeException> expected) {
        var before = ticket.getUpdatedAt();
        boolean archived = ticket.isArchived();
        assertThrows(expected,
                () -> service.archiveTicket(new ArchiveTicketRequest(ticket.getId())));
        assertEquals(archived, ticket.isArchived());
        assertUnchanged(before);
    }

    private void assertUnchanged(LocalDateTime updatedAt) {
        assertEquals(updatedAt, ticket.getUpdatedAt());
        verify(tickets, never()).save(any());
        verifyNoInteractions(history);
    }

    private void assertHistory(User actor, String field, String oldValue, String newValue) {
        var captor = ArgumentCaptor.forClass(TicketHistory.class);
        verify(history).save(captor.capture());
        var event = captor.getValue();
        assertSame(ticket, event.getTicket());
        assertSame(actor, event.getActor());
        assertEquals(field, event.getFieldName());
        assertEquals(oldValue, event.getOldValue());
        assertEquals(newValue, event.getNewValue());
        assertNotNull(event.getCreatedAt());
    }
}
