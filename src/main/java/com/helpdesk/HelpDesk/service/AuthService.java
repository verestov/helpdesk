package com.helpdesk.HelpDesk.service;

import com.helpdesk.HelpDesk.auth.JwtCore;
import com.helpdesk.HelpDesk.auth.UserDetailsImpl;
import com.helpdesk.HelpDesk.dto.login.LoginRequest;
import com.helpdesk.HelpDesk.dto.login.LoginResponse;
import com.helpdesk.HelpDesk.dto.register.RegisterRequest;
import com.helpdesk.HelpDesk.dto.user.UserResponse;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.exception.ResourceAlreadyExistsException;
import com.helpdesk.HelpDesk.mapper.UserMapper;
import com.helpdesk.HelpDesk.repository.UserRepository;
import org.jspecify.annotations.NonNull;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
public class AuthService {
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtCore jwtCore;
    private final UserMapper userMapper;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtCore jwtCore,
            UserMapper userMapper
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtCore = jwtCore;
        this.userMapper = userMapper;
    }

    @Transactional
    public UserResponse register(
            @NonNull RegisterRequest registerRequest
    ) {
        if (userRepository.existsByEmail(registerRequest.email())) {
            throw new ResourceAlreadyExistsException("Email already exists");
        }
        var newUser = new User();
        newUser.setFullName(registerRequest.fullName());
        newUser.setEmail(registerRequest.email());
        newUser.setPassword(passwordEncoder.encode(registerRequest.password()));
        newUser.setRole(Roles.CUSTOMER);
        newUser.setEnabled(true);
        newUser.setCreatedAt(LocalDateTime.now());

        var savedUser = userRepository.save(newUser);

        return userMapper.toDTO(savedUser);
    }

    public LoginResponse login(
            @NonNull LoginRequest loginRequest
    ) {
        Authentication authentication = authenticationManager.authenticate(
                UsernamePasswordAuthenticationToken.unauthenticated(
                        loginRequest.email(),
                        loginRequest.password()
                )
        );

        String token = jwtCore.generateToken(authentication);

        var principal = (UserDetailsImpl) authentication.getPrincipal();
        assert principal != null;
        var user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new LoginResponse(token, userMapper.toDTO(user));
    }
}
