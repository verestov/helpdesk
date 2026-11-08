package com.helpdesk.HelpDesk.entity;

import com.helpdesk.HelpDesk.enums.Roles;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class User {
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "email", nullable = false, unique = true)
   private String email;

   @Column(name = "password", nullable = false)
   private String password;

   @Column(name = "full_name", nullable = false, length = 150)
   private String fullName;

   @Column(name = "role", nullable = false, length = 30)
   @Enumerated(EnumType.STRING)
   private Roles role;

   @OneToMany(mappedBy = "createdBy")
   private List<Ticket> createdTickets;

   @OneToMany(mappedBy = "assignedTo")
   private List<Ticket> assignedTickets;
   
   @Column(name = "created_at", nullable = false)
   private LocalDateTime createdAt;

   @OneToMany(mappedBy = "author")
   private List<Comment> comments;
}