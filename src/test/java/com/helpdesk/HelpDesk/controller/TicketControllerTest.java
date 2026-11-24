package com.helpdesk.HelpDesk.controller;

import com.helpdesk.HelpDesk.auth.JwtCore;
import com.helpdesk.HelpDesk.auth.UserDetailsImpl;
import com.helpdesk.HelpDesk.entity.Category;
import com.helpdesk.HelpDesk.entity.User;
import com.helpdesk.HelpDesk.enums.Roles;
import com.helpdesk.HelpDesk.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Transactional
class TicketControllerTest {
    static final String URL = "/api/v1/tickets";
    @Autowired WebApplicationContext context;
    @Autowired FilterChainProxy security;
    @Autowired UserRepository users;
    @Autowired CategoryRepository categories;
    @Autowired TicketRepository tickets;
    @Autowired JwtCore jwt;
    @Autowired ObjectMapper json;

    private MockMvc mvc;
    private User customer, support, admin;
    private Long categoryId;

    @BeforeEach
    void setup() {
        mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(security).build();
        customer = account(Roles.CUSTOMER);
        support = account(Roles.SUPPORT);
        admin = account(Roles.ADMIN);
        var category = new Category();
        category.setName(UUID.randomUUID().toString());
        category.setCreatedAt(LocalDateTime.now());
        categoryId = categories.save(category).getId();
    }

    private User account(Roles role) {
        var user = new User();
        user.setEmail(UUID.randomUUID() + "@example.test");
        user.setFullName(role.name());
        user.setPassword("unused");
        user.setRole(role);
        user.setEnabled(true);
        user.setCreatedAt(LocalDateTime.now());
        return users.save(user);
    }

    private String bearer(User actor) {
        var principal = UserDetailsImpl.build(actor);
        return "Bearer " + jwt.generateToken(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, principal.getAuthorities()));
    }

    private long create(User owner) throws Exception {
        var result = mvc.perform(post(URL).header("Authorization", bearer(owner))
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"title":"Help","description":"Printer offline","priority":"LOW","categoryId":%d}
                        """.formatted(categoryId)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.createdBy.id").value(owner.getId()))
                .andExpect(jsonPath("$.status").value("OPEN")).andReturn();
        return json.readTree(result.getResponse().getContentAsByteArray()).get("id").asLong();
    }

    private ResultActions change(User actor, long id, String field, String body) throws Exception {
        return mvc.perform(patch(URL + "/{id}/{field}", id, field).header("Authorization", bearer(actor))
                .contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    void staffProcessesTicketAndAdminArchivesIt() throws Exception {
        long id = create(customer);
        var ticket = tickets.findById(id).orElseThrow();
        ticket.setCreatedAt(LocalDateTime.now().minusMonths(2));
        change(support, id, "status", "{\"status\":\"IN_PROGRESS\"}").andExpect(status().isOk());
        change(support, id, "priority", "{\"priority\":\"HIGH\"}").andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("HIGH"));
        change(support, id, "assignee", "{\"assigneeId\":" + support.getId() + "}").andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo.id").value(support.getId()));
        change(support, id, "assignee", "{\"assigneeId\":null}").andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo").isEmpty());
        change(admin, id, "status", "{\"status\":\"RESOLVED\"}").andExpect(status().isOk());
        change(support, id, "archive", "").andExpect(status().isForbidden());
        change(admin, id, "archive", "").andExpect(status().isOk());
        mvc.perform(get(URL + "/archive").header("Authorization", bearer(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get(URL + "/{id}", id).header("Authorization", bearer(customer)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.isArchived").value(true));
    }

    @Test
    void listsBindFiltersAndPaginationAndRespectOwnership() throws Exception {
        var other = account(Roles.CUSTOMER);
        long foreign = create(other);
        long id = create(customer);
        create(customer);
        change(support, id, "priority", "{\"priority\":\"HIGH\"}").andExpect(status().isOk());
        change(support, id, "assignee", "{\"assigneeId\":" + support.getId() + "}").andExpect(status().isOk());
        mvc.perform(get(URL).header("Authorization", bearer(support)).param("status","OPEN")
                .param("priority","HIGH").param("categoryId",categoryId.toString())
                .param("assigneeId",support.getId().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get(URL + "/mine").header("Authorization", bearer(customer))
                .param("size","1").param("page","0").param("sort","id,asc"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.content.length()").value(1)).andExpect(jsonPath("$.content[0].id").value(id));
        mvc.perform(get(URL + "/{id}", foreign).header("Authorization", bearer(customer)))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @CsvSource({"status,{}", "priority,{}", "assignee,{}", "assignee,{\"assigneeId\":-1}"})
    void rejectsInvalidPatchBody(String field, String body) throws Exception {
        change(support, 1L, field, body).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsMissingJwtInvalidFiltersAndMissingTicket() throws Exception {
        mvc.perform(get(URL)).andExpect(status().isUnauthorized());
        mvc.perform(post(URL).header("Authorization", bearer(customer)).contentType(MediaType.APPLICATION_JSON)
                .content("{}")).andExpect(status().isBadRequest());
        mvc.perform(get(URL).header("Authorization", bearer(customer)).param("status","UNKNOWN"))
                .andExpect(status().isBadRequest());
        mvc.perform(get(URL + "/999999999").header("Authorization", bearer(admin))).andExpect(status().isNotFound());
    }
}
