package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.ticket.CreateTicketRequest;
import com.helpdesk.HelpDesk.dto.ticket.TicketResponse;
import com.helpdesk.HelpDesk.dto.ticket.UpdateTicketPriorityRequest;
import com.helpdesk.HelpDesk.dto.ticket.UpdateTicketStatusRequest;
import com.helpdesk.HelpDesk.entity.Ticket;
import com.helpdesk.HelpDesk.entity.TicketHistory;
import com.helpdesk.HelpDesk.enums.TicketStatus;
import com.helpdesk.HelpDesk.exception.*;
import com.helpdesk.HelpDesk.mapper.TicketMapper;
import com.helpdesk.HelpDesk.repository.CategoryRepository;
import com.helpdesk.HelpDesk.repository.TicketHistoryRepository;
import com.helpdesk.HelpDesk.repository.TicketRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;
    private final CategoryRepository categoryRepository;
    private final TicketHistoryRepository ticketHistoryRepository;

    public TicketService(
            TicketRepository ticketRepository,
            TicketMapper ticketMapper,
            CategoryRepository categoryRepository,
            TicketHistoryRepository ticketHistoryRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketMapper = ticketMapper;
        this.categoryRepository = categoryRepository;
        this.ticketHistoryRepository = ticketHistoryRepository;
    }

    @Transactional
    public TicketResponse createTicket(@NonNull CreateTicketRequest request) {
        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category dose not exists"));

        var newTicket = new Ticket();
        newTicket.setTitle(request.title());
        newTicket.setDescription(request.description());
        newTicket.setStatus(TicketStatus.OPEN);
        newTicket.setPriority(request.priority());
        // FIXME: id <- Spring Security
        newTicket.setAssignedTo(null);
        newTicket.setCategory(category);
        newTicket.setCreatedAt(LocalDateTime.now());
        newTicket.setUpdatedAt(LocalDateTime.now());
        newTicket.setArchived(false);

        var ticketToSave = ticketRepository.save(newTicket);

        return ticketMapper.toDTO(ticketToSave);
    }

    public List<TicketResponse> getAllTickets() {
        List<Ticket> tickets = ticketRepository.findAllByIsArchivedFalse();

        return tickets.stream()
                .map(ticketMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(Long id) {
        var ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        return ticketMapper.toDTO(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketResponse> getTicketsByStatus(@NonNull TicketStatus status){
        List<Ticket> tickets = ticketRepository.findByStatus(status);

        return tickets.stream()
                .map(ticketMapper::toDTO)
                .toList();
    }

    @Transactional
    public TicketResponse updateTicketStatus(
            Long id,
            @NonNull UpdateTicketStatusRequest request
    ) {
        var ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        boolean isValidStatusUpdating = isValidStatusTransition(
                ticket.getStatus(),
                request.status()
        );

        if(isValidStatusUpdating){
            saveTicketHistory(
                    ticket,
                    ticket.getStatus().name(),
                    request.status().name(),
                    request
            );

            ticket.setStatus(request.status());
            ticket.setUpdatedAt(LocalDateTime.now());
            var savedTicket = ticketRepository.save(ticket);

            return  ticketMapper.toDTO(savedTicket);
        } else {
            throw new ImpossibleToChangeStatusException("Invalid status transition");
        }
    }

    private void saveTicketHistory(
            Ticket ticket,
            String oldValue,
            String newValue,
            @NonNull UpdateTicketStatusRequest request

    ){
        var newTicketHistory = new TicketHistory();
        newTicketHistory.setTicket(ticket);

        // FIXME: actor <- Spring Security

        newTicketHistory.setFieldName("status");
        newTicketHistory.setOldValue(oldValue);
        newTicketHistory.setNewValue(newValue);
        newTicketHistory.setCreatedAt(LocalDateTime.now());

        ticketHistoryRepository.save(newTicketHistory);
    }

    private boolean isValidStatusTransition(
            @NonNull TicketStatus currentStatus,
            TicketStatus newStatus
    ) {
        return switch (currentStatus) {
            case OPEN -> newStatus == TicketStatus.IN_PROGRESS;
            case IN_PROGRESS -> newStatus == TicketStatus.WAITING_FOR_CUSTOMER
                    || newStatus == TicketStatus.RESOLVED;
            case WAITING_FOR_CUSTOMER -> newStatus == TicketStatus.IN_PROGRESS
                    || newStatus == TicketStatus.RESOLVED;
            case RESOLVED -> newStatus == TicketStatus.CLOSED;
            case CLOSED -> false;
        };
    }

    @Transactional
    public TicketResponse updateTicketPriority(
            Long id,
            @NonNull UpdateTicketPriorityRequest request
    ) {
        var ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        ticket.setPriority(request.priority());

        var savedTicket = ticketRepository.save(ticket);

        return ticketMapper.toDTO(savedTicket);
    }
}
