package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.ticket.CreateTicketRequest;
import com.helpdesk.HelpDesk.dto.ticket.TicketResponse;
import com.helpdesk.HelpDesk.dto.ticket.UpdateTicketPriorityRequest;
import com.helpdesk.HelpDesk.dto.ticket.UpdateTicketStatusRequest;
import com.helpdesk.HelpDesk.entity.Ticket;
import com.helpdesk.HelpDesk.enums.Priority;
import com.helpdesk.HelpDesk.enums.TicketStatus;
import com.helpdesk.HelpDesk.exception.CategoryNotFoundException;
import com.helpdesk.HelpDesk.exception.PriorityNotFoundException;
import com.helpdesk.HelpDesk.exception.StatusNotFoundException;
import com.helpdesk.HelpDesk.exception.TicketNotFoundException;
import com.helpdesk.HelpDesk.mapper.TicketMapper;
import com.helpdesk.HelpDesk.repository.CategoryRepository;
import com.helpdesk.HelpDesk.repository.TicketRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TicketService {
    private final TicketRepository ticketRepository;
    private final TicketMapper ticketMapper;
    private final CategoryRepository categoryRepository;

    public TicketService(
            TicketRepository ticketRepository,
            TicketMapper ticketMapper,
            CategoryRepository categoryRepository
    ) {
        this.ticketRepository = ticketRepository;
        this.ticketMapper = ticketMapper;
        this.categoryRepository = categoryRepository;
    }

    @Transactional
    public TicketResponse createTicket(@NonNull CreateTicketRequest request) {
        var category = categoryRepository.findById(request.categoryId())
                .orElseThrow(() -> new CategoryNotFoundException("Category dose not exists"));

        var newTicket = new Ticket();
        newTicket.setTitle(request.title());
        newTicket.setDescription(request.description());
        newTicket.setPriority(request.priority());
        newTicket.setCategory(category);

        // FIXME: Допилить переменные, которые я не утсановил

        var ticketToSave = ticketRepository.save(newTicket);

        return ticketMapper.toDTO(ticketToSave);
    }

    public List<TicketResponse> getAllTickets() {
        // FIXME: Здесь будет проблема N+1
        List<Ticket> tickets = ticketRepository.findAll();

        return tickets.stream()
                .map(ticketMapper::toDTO)
                .toList();
    }

    @Transactional(readOnly = true)
    public TicketResponse getTicketById(@NonNull Long id) {
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
    public TicketResponse updateTicketStatus(@NonNull Long id, UpdateTicketStatusRequest request) {
        var ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        // FIXME: Слишком свободная реализация обновления. Если тикет в статусе CLOSED, его обновлять нельзя
        ticket.setStatus(request.status());

        var savedTicket = ticketRepository.save(ticket);

        return  ticketMapper.toDTO(savedTicket);
    }

    @Transactional
    public TicketResponse updateTicketPriority(@NonNull Long id, UpdateTicketPriorityRequest request) {
        var ticket = ticketRepository.findById(id)
                .orElseThrow(() -> new TicketNotFoundException("Ticket not found"));

        ticket.setPriority(request.priority());

        var savedTicket = ticketRepository.save(ticket);

        return ticketMapper.toDTO(savedTicket);
    }
}
