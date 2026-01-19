package com.glideclouds.taskmanagementsystem.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glideclouds.taskmanagementsystem.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.hamcrest.Matchers.hasSize;

@SpringBootTest
@AutoConfigureMockMvc
class AdminControllerTest extends AbstractMongoIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void adminEndpoints_requireAdminRole() throws Exception {
        // 1. Regular user login
        String userToken = registerAndLogin("regular@test.com", "Password123!");

        // 2. Try to access admin endpoint
        mvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void listUsers_worksForAdmin() throws Exception {
        // 1. Admin login (email starts with 'admin')
        String adminToken = registerAndLogin("admin@test.com", "Password123!");

        // 2. Access admin endpoint
        mvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1))); // Should contain at least the admin itself
    }

    @Test
    void createGroup_works() throws Exception {
        String adminToken = registerAndLogin("admin2@test.com", "Password123!");

        // Create a user to add to group
        registerAndLogin("member@test.com", "Password123!");

        String groupBody = objectMapper.writeValueAsString(new CreateGroupRequest("Dev Team", List.of("member@test.com")));

        mvc.perform(post("/api/admin/groups")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(groupBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Dev Team"));
    }

    private String registerAndLogin(String email, String password) throws Exception {
        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthBody(email, password))))
                .andExpect(status().isCreated());

        String loginJson = mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AuthBody(email, password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(loginJson).get("token").asText();
    }

    private record AuthBody(String email, String password) {}
    private record CreateGroupRequest(String name, List<String> memberEmails) {}
}
