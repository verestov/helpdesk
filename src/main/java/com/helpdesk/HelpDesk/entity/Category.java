package com.helpdesk.HelpDesk.entity;

import java.time.LocalDateTime;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.List;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Category {
   @Id
   @GeneratedValue(strategy = GenerationType.IDENTITY)
   private Long id;

   @Column(name = "name", nullable = false, length = 100)
   private String name;

   @Column(name = "created_at", nullable = false)
   private LocalDateTime createdAt;

   @Column(name = "description", length = 500)
   private String description;

   @OneToMany(mappedBy = "category")
   private List<Ticket> tickets;
}