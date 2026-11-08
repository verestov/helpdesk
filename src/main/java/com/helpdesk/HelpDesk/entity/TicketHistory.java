package com.helpdesk.HelpDesk.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Entity
@Table(name = "ticket_histories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TicketHistory {
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "ticket_id", nullable = false)
   private Ticket ticket;

   @ManyToOne(fetch = FetchType.LAZY)
   @JoinColumn(name = "actor_id", nullable = false)
   private User actor;

   @Column(name = "field_name", nullable = false, length = 50)
   private String fieldName;

   @Column(name = "old_value")
   private String oldValue;

   @Column(name = "new_value", nullable = false)
   private String newValue;

   @Column(name = "created_at", nullable = false)
   private LocalDateTime createdAt;
}