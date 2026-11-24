package com.helpdesk.HelpDesk.auth;

import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class AuthEndpointsTest {
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtCore jwt;
    @Autowired ObjectMapper json;
    private MockMvc mvc;
    private final String email = UUID.randomUUID() + "@example.test";
    private static final String PASSWORD = "test-password";

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
    }

    @Test
    void newCustomerCanLogInWithTheRegisteredPassword() throws Exception {
        register(PASSWORD).andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.role").value("CUSTOMER"))
                .andExpect(jsonPath("$.password").doesNotExist());
        User saved = users.findByEmail(email).orElseThrow();
        assertTrue(saved.isEnabled());
        assertNotEquals(PASSWORD, saved.getPassword());
        assertTrue(encoder.matches(PASSWORD, saved.getPassword()));

        var response = login(PASSWORD).andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(saved.getId()))
                .andExpect(jsonPath("$.user.password").doesNotExist())
                .andReturn().getResponse();
        var token = json.readTree(response.getContentAsByteArray()).get("token").asString();
        assertEquals(saved.getId(), jwt.getIdFromToken(token));
    }

    @Test
    void duplicateRegistrationCannotReplaceExistingCredentials() throws Exception {
        register(PASSWORD).andExpect(status().isCreated());
        var original = users.findByEmail(email).orElseThrow();
        var hash = original.getPassword();
        register("another-password").andExpect(status().isConflict());
        var after = users.findByEmail(email).orElseThrow();
        assertEquals(original.getId(), after.getId());
        assertEquals(hash, after.getPassword());
    }

    @Test
    void rejectsShortPasswordBeforeCreatingAccount() throws Exception {
        register("short").andExpect(status().isBadRequest());
        assertFalse(users.existsByEmail(email));
    }

    @Test
    void wrongPasswordReturnsUnauthorizedWithoutToken() throws Exception {
        register(PASSWORD).andExpect(status().isCreated());
        login("incorrect").andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    private ResultActions register(String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Test Customer","email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }

    private ResultActions login(String password) throws Exception {
        return mvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(email, password)));
    }
}
