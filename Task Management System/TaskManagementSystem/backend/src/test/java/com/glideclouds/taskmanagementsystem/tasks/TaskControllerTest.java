package com.glideclouds.taskmanagementsystem.tasks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.glideclouds.taskmanagementsystem.AbstractMongoIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest extends AbstractMongoIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;

    @Test
    void createListMoveTasks_worksWithJwt() throws Exception {
        String token = registerAndLogin();

        JsonNode t1 = createTask(token, "A");
        JsonNode t2 = createTask(token, "B");

        mvc.perform(get("/api/tasks/" + t1.get("id").asText())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String listJson = mvc.perform(get("/api/tasks")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode list = objectMapper.readTree(listJson);
        assertThat(list.isArray()).isTrue();
        assertThat(list.size()).isGreaterThanOrEqualTo(2);

        String moveBody = objectMapper.writeValueAsString(new MoveBody(t2.get("id").asText(), "TODO", "IN_PROGRESS", 0));
        mvc.perform(post("/api/tasks/move")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(moveBody))
                .andExpect(status().isOk());

        // time budget update
        String timeBudgetBody = "{\"timeBudgetMinutes\":60}";
        mvc.perform(put("/api/tasks/" + t1.get("id").asText() + "/time-budget")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(timeBudgetBody))
                .andExpect(status().isOk());

        // recurrence update (weekly Mon/Wed)
        String recurrenceBody = "{\"frequency\":\"WEEKLY\",\"interval\":1,\"weekdaysOnly\":false,\"daysOfWeek\":[1,3],\"endDate\":null,\"nthBusinessDayOfMonth\":null}";
        mvc.perform(put("/api/tasks/" + t1.get("id").asText() + "/recurrence")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(recurrenceBody))
                .andExpect(status().isOk());
    }

    private String registerAndLogin() throws Exception {
        String email = "user2@example.com";
        String password = "Password123!";

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

    private JsonNode createTask(String token, String title) throws Exception {
        String body = objectMapper.writeValueAsString(new CreateBody(title, "desc", "MEDIUM"));
        String json = mvc.perform(post("/api/tasks")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(json);
    }

    private record AuthBody(String email, String password) {}
    private record CreateBody(String title, String description, String priority) {}
    private record MoveBody(String taskId, String fromStatus, String toStatus, int toIndex) {}
}
