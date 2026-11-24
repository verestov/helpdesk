package com.helpdesk.HelpDesk.repository;

import com.helpdesk.HelpDesk.entity.Ticket;
import com.helpdesk.HelpDesk.enums.TicketStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface TicketRepository extends JpaRepository<Ticket, Long>, JpaSpecificationExecutor<Ticket> {
    @Override
    @EntityGraph(attributePaths = {"createdBy", "assignedTo", "category"})
    Page<Ticket> findAll(Specification<Ticket> specification, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from Ticket t where t.id = :id")
    Optional<Ticket> findByIdForUpdate(@Param("id") Long id);

    @EntityGraph(attributePaths = {"createdBy", "assignedTo", "category"})
    List<Ticket> findAllByIsArchivedFalse();
    Ticket findByIdAndIsArchivedFalse(Long id);
    List<Ticket> findByStatus(TicketStatus status);

    @Query("""
            SELECT t FROM Ticket t
            JOIN FETCH t.createdBy creator
            LEFT JOIN FETCH t.assignedTo
            JOIN FETCH t.category
            WHERE creator.id = :userId
            """)
    List<Ticket> findTicketsByCreatorId(@Param("userId") Long userId);
}
