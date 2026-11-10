package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.register.RegisterRequest;
import com.helpdesk.HelpDesk.dto.user.UpdateUserRoleRequest;
import com.helpdesk.HelpDesk.dto.user.UpdateUserStatusRequest;
import com.helpdesk.HelpDesk.dto.user.UserResponse;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.exception.BadUpdateRequestException;
import com.helpdesk.HelpDesk.exception.EmailAlreadyExistsException;
import com.helpdesk.HelpDesk.exception.UserNotFoundException;
import com.helpdesk.HelpDesk.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

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

        return mapToUserResponse(savedUser);
    }

    public UserResponse getUserById(Long id) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return mapToUserResponse(user);
    }

    public List<UserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();

        return users.stream()
                .map(this::mapToUserResponse)
                .toList();
    }

    @Transactional
    public UserResponse updateUserRole(Long id, UpdateUserRoleRequest request) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        if(user.getRole() == Roles.ADMIN) {
            throw new BadUpdateRequestException("Role ADMIN can not be changed");
        }

        user.setRole(request.role());
        var savedUser = userRepository.save(user);
        return mapToUserResponse(savedUser);
    }

    @Transactional
    public UserResponse updateUserStatus(Long id, UpdateUserStatusRequest request) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));
        var countOfAdmins = userRepository.countActiveAdmins(Roles.ADMIN);

        if (countOfAdmins == 2 && user.getRole() == Roles.ADMIN && !request.isEnabled()) {
            throw new BadUpdateRequestException("Last ADMIN can not be disabled");
        }

        // TODO: Реализовать проверку на попытку заблокировать самого себя

        user.setEnabled(request.isEnabled());
        var savedUser = userRepository.save(user);
        return mapToUserResponse(savedUser);
    }

    private UserResponse mapToUserResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getEmail(),
                user.getFullName(),
                user.getRole()
        );
    }
}