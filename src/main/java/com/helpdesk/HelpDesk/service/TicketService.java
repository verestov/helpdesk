package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.auth.UserDetailsImpl;
import com.helpdesk.HelpDesk.dto.ticket.*;
import com.helpdesk.HelpDesk.entity.Ticket;
import com.helpdesk.HelpDesk.entity.TicketHistory;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.enums.TicketStatus;
import com.helpdesk.HelpDesk.exception.BusinessRuleViolationException;
import com.helpdesk.HelpDesk.exception.ResourceNotFoundException;
import com.helpdesk.HelpDesk.mapper.TicketMapper;
import com.helpdesk.HelpDesk.repository.CategoryRepository;
import com.helpdesk.HelpDesk.repository.TicketHistoryRepository;
import com.helpdesk.HelpDesk.repository.TicketRepository;
import com.helpdesk.HelpDesk.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
@Transactional(readOnly = true)
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;
    private final CategoryRepository categoryRepository;
    private final TicketHistoryRepository ticketHistoryRepository;
    private final UserRepository userRepository;

    public TicketService(
            TicketRepository ticketRepository,
            TicketMapper ticketMapper,
            CategoryRepository categoryRepository,
            TicketHistoryRepository ticketHistoryRepository,
            UserRepository userRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketMapper = ticketMapper;
        this.categoryRepository = categoryRepository;
        this.ticketHistoryRepository = ticketHistoryRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public TicketResponse createTicket(CreateTicketRequest request) {
        User creator = getCurrentUser();
        if (creator.getRole() != Roles.CUSTOMER) {
            throw new AccessDeniedException("Only customers can create tickets");
        }
        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Category not found"));

        var ticket = new Ticket();
        ticket.setTitle(request.title());
        ticket.setDescription(request.description());
        ticket.setStatus(TicketStatus.OPEN);
        ticket.setPriority(request.priority());
        ticket.setCreatedBy(creator);
        ticket.setCategory(category);
        var now = LocalDateTime.now();
        ticket.setCreatedAt(now);
        ticket.setUpdatedAt(now);
        return ticketMapper.toDTO(ticketRepository.save(ticket));
    }

    public TicketResponse getTicketById(Long id) {
        User user = getCurrentUser();
        var ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        if (!isStaff(user) && !ticket.getCreatedBy().getId().equals(user.getId())) {
            throw new AccessDeniedException("Customers can only view their own tickets");
        }
        return ticketMapper.toDTO(ticket);
    }

    public List<TicketResponse> getAllTickets() {
        return getTickets(TicketFilter.ALL, Pageable.unpaged()).getContent();
    }

    public List<TicketResponse> getTicketsByStatus(TicketStatus status) {
        var filter = new TicketFilter(Objects.requireNonNull(status), null, null, null);
        return getTickets(filter, Pageable.unpaged()).getContent();
    }

    /** Staff see the working queue. Customers see all their tickets, including archived ones. */
    public Page<TicketResponse> getTickets(TicketFilter filter, Pageable pageable) {
        User user = getCurrentUser();
        if (isStaff(user)) {
            return findTickets(null, false, filter, pageable);
        }
        return findTickets(user.getId(), null, filter, pageable);
    }

    public Page<TicketResponse> getMyTickets(TicketFilter filter, Pageable pageable) {
        return findTickets(getCurrentUser().getId(), null, filter, pageable);
    }

    public Page<TicketResponse> getArchivedTickets(TicketFilter filter, Pageable pageable) {
        User user = getCurrentUser();
        Long creatorId = isStaff(user) ? null : user.getId();
        return findTickets(creatorId, true, filter, pageable);
    }

    private Page<TicketResponse> findTickets(Long creatorId, Boolean archived, TicketFilter filter, Pageable pageable) {
        Specification<Ticket> specification = (ticket, query, builder) -> {
            var conditions = new ArrayList<Predicate>();
            if (creatorId != null) {
                conditions.add(builder.equal(ticket.get("createdBy").get("id"), creatorId));
            }
            if (archived != null) {
                conditions.add(builder.equal(ticket.get("isArchived"), archived));
            }
            if (filter.status() != null) {
                conditions.add(builder.equal(ticket.get("status"), filter.status()));
            }
            if (filter.priority() != null) {
                conditions.add(builder.equal(ticket.get("priority"), filter.priority()));
            }
            if (filter.categoryId() != null) {
                conditions.add(builder.equal(ticket.get("category").get("id"), filter.categoryId()));
            }
            if (filter.assigneeId() != null) {
                conditions.add(builder.equal(ticket.get("assignedTo").get("id"), filter.assigneeId()));
            }
            return builder.and(conditions);
        };
        return ticketRepository.findAll(specification, sortedPage(pageable)).map(ticketMapper::toDTO);
    }

    private Pageable sortedPage(Pageable pageable) {
        Sort sort = pageable.getSortOr(Sort.by(Sort.Direction.DESC, "createdAt"));
        for (Sort.Order order : sort) {
            if (!order.getProperty().equals("createdAt") && !order.getProperty().equals("id")) {
                throw new BusinessRuleViolationException("Tickets can be sorted by createdAt or id");
            }
        }
        if (sort.getOrderFor("id") == null) {
            sort = sort.and(Sort.by(Sort.Direction.DESC, "id"));
        }
        return pageable.isPaged()
                ? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)
                : Pageable.unpaged(sort);
    }

    @Transactional
    public TicketResponse updateTicketStatus(Long id, UpdateTicketStatusRequest request) {
        User actor = requireStaff();
        Ticket ticket = getMutableTicket(id);
        var oldStatus = ticket.getStatus();
        if (!isValidStatusTransition(oldStatus, request.status())) {
            throw new BusinessRuleViolationException("Invalid status transition");
        }
        ticket.setStatus(request.status());
        return recordChange(ticket, actor, "status", oldStatus.name(), request.status().name());
    }

    @Transactional
    public TicketResponse updateTicketPriority(Long id, UpdateTicketPriorityRequest request) {
        User actor = requireStaff();
        Ticket ticket = getMutableTicket(id);
        var oldPriority = ticket.getPriority();
        if (oldPriority == request.priority()) {
            return ticketMapper.toDTO(ticket);
        }
        ticket.setPriority(request.priority());
        return recordChange(ticket, actor, "priority", oldPriority.name(), request.priority().name());
    }

    @Transactional
    public TicketResponse updateTicketAssignee(Long id, UpdateTicketAssigneeRequest request) {
        User actor = requireStaff();
        Ticket ticket = getMutableTicket(id);
        if (ticket.getStatus() == TicketStatus.CLOSED) {
            throw new BusinessRuleViolationException("Cannot change the assignee of a closed ticket");
        }
        Long oldId = ticket.getAssignedTo() == null ? null : ticket.getAssignedTo().getId();
        Long newId = request.assigneeId();
        User assignee = null;
        if (newId != null) {
            assignee = userRepository.findById(newId)
                    .orElseThrow(() -> new ResourceNotFoundException("Assignee not found"));
            if (!assignee.isEnabled() || !isStaff(assignee)) {
                throw new BusinessRuleViolationException("Assignee must be an active support user or admin");
            }
        }
        if (Objects.equals(oldId, newId)) {
            return ticketMapper.toDTO(ticket);
        }
        ticket.setAssignedTo(assignee);
        return recordChange(ticket, actor, "assignedTo", Objects.toString(oldId, null), Objects.toString(newId, null));
    }

    @Transactional
    public TicketResponse archiveTicket(ArchiveTicketRequest request) {
        User actor = getCurrentUser();
        if (actor.getRole() != Roles.ADMIN) {
            throw new AccessDeniedException("Only admins can archive tickets");
        }
        Ticket ticket = getMutableTicket(request.id());
        if (ticket.getStatus() != TicketStatus.RESOLVED && ticket.getStatus() != TicketStatus.CLOSED) {
            throw new BusinessRuleViolationException("Only resolved or closed tickets can be archived");
        }
        if (LocalDateTime.now().isBefore(ticket.getCreatedAt().plusMonths(1))) {
            throw new BusinessRuleViolationException("Ticket must be at least one calendar month old");
        }
        ticket.setArchived(true);
        return recordChange(ticket, actor, "archived", "false", "true");
    }

    private Ticket getMutableTicket(Long id) {
        var ticket = ticketRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ticket not found"));
        if (ticket.isArchived()) {
            throw new BusinessRuleViolationException("Archived tickets are read-only");
        }
        return ticket;
    }

    private TicketResponse recordChange(Ticket ticket, User actor, String field, String oldValue, String newValue) {
        var now = LocalDateTime.now();
        ticket.setUpdatedAt(now);
        var history = new TicketHistory();
        history.setTicket(ticket);
        history.setActor(actor);
        history.setFieldName(field);
        history.setOldValue(oldValue);
        history.setNewValue(newValue);
        history.setCreatedAt(now);
        ticketHistoryRepository.save(history);
        return ticketMapper.toDTO(ticketRepository.save(ticket));
    }

    private User getCurrentUser() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof UserDetailsImpl principal)) {
            throw new AuthenticationCredentialsNotFoundException("Authentication required");
        }
        return userRepository.findById(principal.getId()).filter(User::isEnabled)
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("Active account required"));
    }

    private User requireStaff() {
        var actor = getCurrentUser();
        if (!isStaff(actor)) {
            throw new AccessDeniedException("Only staff can change tickets");
        }
        return actor;
    }

    private boolean isStaff(User user) {
        return user.getRole() == Roles.ADMIN || user.getRole() == Roles.SUPPORT;
    }

    private boolean isValidStatusTransition(TicketStatus currentStatus, TicketStatus newStatus) {
        return switch (currentStatus) {
            case OPEN -> newStatus == TicketStatus.IN_PROGRESS;
            case IN_PROGRESS -> newStatus == TicketStatus.WAITING_FOR_CUSTOMER || newStatus == TicketStatus.RESOLVED;
            case WAITING_FOR_CUSTOMER -> newStatus == TicketStatus.IN_PROGRESS || newStatus == TicketStatus.RESOLVED;
            case RESOLVED -> newStatus == TicketStatus.CLOSED;
            case CLOSED -> false;
        };
    }
}
