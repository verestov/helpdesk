package com.helpdesk.HelpDesk.repository;

import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Roles;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);

    @Query("SELECT COUNT(u) FROM User u WHERE u.role = :role AND u.isEnabled = true")
    long countActiveAdmins(@Param("role") Roles role);
}
