package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.dto.user.UpdateUserRoleRequest;
import com.helpdesk.HelpDesk.dto.user.UpdateUserStatusRequest;
import com.helpdesk.HelpDesk.dto.user.UserResponse;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.exception.BadUpdateRequestException;
import com.helpdesk.HelpDesk.exception.UserNotFoundException;
import com.helpdesk.HelpDesk.mapper.UserMapper;
import com.helpdesk.HelpDesk.repository.UserRepository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {
    private final UserRepository userRepository;
    private final UserMapper userMapper;

    @Autowired
    public UserService(
            UserRepository userRepository,
            UserMapper userMapper
    ) {
        this.userRepository = userRepository;
        this.userMapper = userMapper;
    }

    public UserResponse getUserById(Long id) {
        var user = userRepository.findById(id)
                .orElseThrow(() -> new UserNotFoundException("User not found"));

        return userMapper.toDTO(user);
    }

    public List<UserResponse> getAllUsers() {
        List<User> users = userRepository.findAll();

        return users.stream()
                .map(userMapper::toDTO)
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
        return userMapper.toDTO(savedUser);
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
        return userMapper.toDTO(savedUser);
    }
}