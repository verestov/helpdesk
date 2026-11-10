package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.register.RegisterRequest;
import com.helpdesk.HelpDesk.dto.user.UserResponse;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.exceptions.EmailAlreadyExistsException;
import com.helpdesk.HelpDesk.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private static  final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse createUser(RegisterRequest registerRequest) {
        if(userRepository.existsByEmail(registerRequest.email())) {
            throw new EmailAlreadyExistsException("Email already exists");
        }
        var newUser = new User();
        newUser.setFullName(registerRequest.fullName());
        newUser.setEmail(registerRequest.email());
        newUser.setPassword(passwordEncoder.encode(registerRequest.password()));
        newUser.setRole(Roles.CUSTOMER);
        newUser.setEnabled(true);
        newUser.setCreatedAt(LocalDateTime.now());

        var savedUser = userRepository.save(newUser);

        return new UserResponse(
                savedUser.getId(),
                savedUser.getEmail(),
                savedUser.getFullName(),
                savedUser.getRole()
        );
    }
}