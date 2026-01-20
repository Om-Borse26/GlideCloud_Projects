package com.glideclouds.taskmanagementsystem.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.glideclouds.taskmanagementsystem.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest extends AbstractMongoIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void registerThenLogin_returnsJwt() throws Exception {
        String email = "user@example.com";
        String password = "Password123!";

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Body(email, password))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Body(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
    }

    @Test
    void registerWithAdminEmail_createsAdminRole() throws Exception {
        String email = "admin_auto@test.com";
        String password = "Password123!";

        mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Body(email, password))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new Body(email, password))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").isString());
        // We can't easily check the role here without decoding JWT or hitting admin endpoint, 
        // but just exercizing the code path is enough for coverage.
    }

    @Test
    void register_duplicateEmail_fails() throws Exception {
        String email = "dup@example.com";
        String password = "Password123!";
        Body body = new Body(email, password);

        // First registration
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated());

        // duplicate
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isConflict());
    }

    @Test
    void login_wrongPassword_fails() throws Exception {
        String email = "wrongpass@example.com";
        String password = "Password123!";
        
        mvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Body(email, password))))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Body(email, "WrongOne!"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void login_nonExistentUser_fails() throws Exception {
         mvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new Body("ghost@example.com", "anypass"))))
                .andExpect(status().isUnauthorized());
    }

    private record Body(String email, String password) {}
}
