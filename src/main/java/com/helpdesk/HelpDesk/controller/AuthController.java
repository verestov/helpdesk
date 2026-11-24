package com.helpdesk.HelpDesk.controller;

import com.helpdesk.HelpDesk.dto.login.LoginRequest;
import com.helpdesk.HelpDesk.dto.login.LoginResponse;
import com.helpdesk.HelpDesk.dto.register.RegisterRequest;
import com.helpdesk.HelpDesk.dto.user.UserResponse;
import com.helpdesk.HelpDesk.service.AuthService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/v1/auth/")
public class AuthController {
    private final AuthService authService;
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthController.class);

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<UserResponse> registerUser(
            @Valid @RequestBody RegisterRequest request
    ) {
        LOGGER.info("Register User");

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> loginUser(
            @Valid @RequestBody LoginRequest request
    ) {
        LOGGER.info("Login User");

        return ResponseEntity.status(HttpStatus.OK)
                .body(authService.login(request));
    }
}
