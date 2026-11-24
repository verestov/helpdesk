package com.helpdesk.HelpDesk.controller;

import com.helpdesk.HelpDesk.dto.ticket.*;
import com.helpdesk.HelpDesk.service.TicketService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/v1/tickets")
public class TicketController {
    private final TicketService ticketService;

    public TicketController(TicketService ticketService) {
        this.ticketService = ticketService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TicketResponse createTicket(@Valid @RequestBody CreateTicketRequest request) {
        return ticketService.createTicket(request);
    }

    @GetMapping
    public PagedModel<TicketResponse> getTickets(@ModelAttribute TicketFilter filter, Pageable pageable) {
        return new PagedModel<>(ticketService.getTickets(filter, pageable));
    }

    @GetMapping("/mine")
    public PagedModel<TicketResponse> getMyTickets(@ModelAttribute TicketFilter filter, Pageable pageable) {
        return new PagedModel<>(ticketService.getMyTickets(filter, pageable));
    }

    @GetMapping("/archive")
    public PagedModel<TicketResponse> getArchivedTickets(@ModelAttribute TicketFilter filter, Pageable pageable) {
        return new PagedModel<>(ticketService.getArchivedTickets(filter, pageable));
    }

    @GetMapping("/{id}")
    public TicketResponse getTicketById(@PathVariable Long id) {
        return ticketService.getTicketById(id);
    }

    @PatchMapping("/{id}/status")
    public TicketResponse updateStatus(@PathVariable Long id, @Valid @RequestBody UpdateTicketStatusRequest request) {
        return ticketService.updateTicketStatus(id, request);
    }

    @PatchMapping("/{id}/priority")
    public TicketResponse updatePriority(@PathVariable Long id, @Valid @RequestBody UpdateTicketPriorityRequest request) {
        return ticketService.updateTicketPriority(id, request);
    }

    @PatchMapping("/{id}/assignee")
    public TicketResponse updateAssignee(@PathVariable Long id, @Valid @RequestBody UpdateTicketAssigneeRequest request) {
        return ticketService.updateTicketAssignee(id, request);
    }

    @PatchMapping("/{id}/archive")
    public TicketResponse archiveTicket(@PathVariable Long id) {
        return ticketService.archiveTicket(new ArchiveTicketRequest(id));
    }
}
