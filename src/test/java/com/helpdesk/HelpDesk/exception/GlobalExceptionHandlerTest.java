package com.helpdesk.HelpDesk.exception;

import com.helpdesk.HelpDesk.controller.AuthController;
import com.helpdesk.HelpDesk.service.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.stream.Stream;

import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {
    @Test
    void reportsInvalidRegistrationField() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new AuthController(mock(AuthService.class)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(post("/api/v1/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"fullName":"Jane Doe","email":"invalid","password":"password123"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("MethodArgumentNotValidException"))
                .andExpect(jsonPath("$.detailedMessage").value(startsWith("email: ")))
                .andExpect(jsonPath("$.errorTime").exists());
    }

    static Stream<Arguments> serviceErrors() {
        return Stream.of(
                Arguments.of(new ResourceNotFoundException("Ticket not found"), 404),
                Arguments.of(new ResourceAlreadyExistsException("Email already exists"), 409),
                Arguments.of(new BusinessRuleViolationException("Invalid status transition"), 400),
                Arguments.of(new AccessDeniedException("Only staff can change ticket status"), 403)
        );
    }

    @ParameterizedTest
    @MethodSource("serviceErrors")
    void mapsServiceErrorToHttpResponse(RuntimeException exception, int expectedStatus) throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new FailingController(exception))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mvc.perform(get("/test/error"))
                .andExpect(status().is(expectedStatus))
                .andExpect(jsonPath("$.message").value(exception.getClass().getSimpleName()))
                .andExpect(jsonPath("$.detailedMessage").value(exception.getMessage()))
                .andExpect(jsonPath("$.errorTime").exists());
    }

    @TestComponent
    @RestController
    static class FailingController {
        private final RuntimeException exception;

        FailingController(RuntimeException exception) {
            this.exception = exception;
        }

        @GetMapping("/test/error")
        void fail() {
            throw exception;
        }
    }
}
