package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.ticket.UpdateTicketStatusRequest;
import com.helpdesk.HelpDesk.entity.Ticket;
import com.helpdesk.HelpDesk.enums.TicketStatus;
import com.helpdesk.HelpDesk.exception.ImpossibleToChangeStatusException;
import com.helpdesk.HelpDesk.mapper.TicketMapper;
import com.helpdesk.HelpDesk.repository.CategoryRepository;
import com.helpdesk.HelpDesk.repository.TicketHistoryRepository;
import com.helpdesk.HelpDesk.repository.TicketRepository;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TicketServiceTest {

    @Test
    void shouldRejectTransitionFromOpenToClosed() {
        // Arrange: создаём зависимости-заглушки и настоящий сервис.
        var ticketRepository = mock(TicketRepository.class);
        var ticketMapper = mock(TicketMapper.class);
        var categoryRepository = mock(CategoryRepository.class);
        var historyRepository = mock(TicketHistoryRepository.class);
        var service = new TicketService(
                ticketRepository, ticketMapper, categoryRepository, historyRepository
        );

        var ticket = new Ticket();
        ticket.setId(1L);
        ticket.setStatus(TicketStatus.OPEN);
        var request = new UpdateTicketStatusRequest(TicketStatus.CLOSED);

        // Вместо обращения к БД репозиторий вернёт подготовленный тикет.
        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        // Act + Assert: вызываем метод и проверяем ожидаемое исключение.
        assertThrows(
                ImpossibleToChangeStatusException.class,
                () -> service.updateTicketStatus(1L, request)
        );

        // Запрещённый переход не должен менять статус и сохранять данные.
        assertEquals(TicketStatus.OPEN, ticket.getStatus());
        verify(ticketRepository, never()).save(any(Ticket.class));
        verifyNoInteractions(historyRepository);
    }

    @Test
    void shouldChangeStatusFromOpenToInProgress(){
        var ticketRepository = mock(TicketRepository.class);
        var ticketMapper = mock(TicketMapper.class);
        var categoryRepository = mock(CategoryRepository.class);
        var historyRepository = mock(TicketHistoryRepository.class);
        var service = new TicketService(
                ticketRepository, ticketMapper, categoryRepository, historyRepository
        );

        LocalDateTime createdAt = LocalDateTime.now();

        var ticket = new Ticket();
        ticket.setId(1L);
        ticket.setCreatedAt(createdAt);
        ticket.setStatus(TicketStatus.OPEN);
        var oldUpdatedAt = LocalDateTime.of(2026, 5, 10, 10, 30);
        ticket.setUpdatedAt(oldUpdatedAt);
        var request = new UpdateTicketStatusRequest(TicketStatus.IN_PROGRESS);

        when(ticketRepository.findById(1L)).thenReturn(Optional.of(ticket));

        service.updateTicketStatus(1L, request);

        assertEquals(TicketStatus.IN_PROGRESS, ticket.getStatus());
        assertTrue(ticket.getUpdatedAt().isAfter(oldUpdatedAt));
        verify(ticketRepository).save(ticket);
        verify(historyRepository).save(any());
    }
}
