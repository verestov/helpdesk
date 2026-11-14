package com.helpdesk.HelpDesk.repository;

import com.helpdesk.HelpDesk.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Long> {
    boolean existsByName(String name);

    @Query("SELECT COUNT(t) > 0 FROM Ticket t WHERE t.category.id = :categoryId")
    boolean hasTickets(@Param("categoryId") Long categoryId);

    @Query("SELECT c FROM Category c WHERE c.name = :name")
    List<Category> findByName(@Param("name") String name);

    @Query("SELECT c.name FROM Category c WHERE c.id = :categoryId")
    String getNameById(@Param("categoryId") Long categoryId);
}
