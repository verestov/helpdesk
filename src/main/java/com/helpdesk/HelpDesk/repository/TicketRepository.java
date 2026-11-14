package com.helpdesk.HelpDesk.repository;

import com.helpdesk.HelpDesk.entity.Ticket;
import com.helpdesk.HelpDesk.enums.TicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long> {
    List<Ticket> findByStatus(TicketStatus status);
}
