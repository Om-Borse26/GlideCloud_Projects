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

    @Test
    void misc_updates_work() throws Exception {
        String token = registerAndLogin("misc@test.com", "Pass123!");
        JsonNode task = createTask(token, "Misc Task");
        String taskId = task.get("id").asText();

        // Update Task (PUT)
        String updateBody = "{\"title\":\"Updated Title\",\"description\":\"Updated Desc\",\"priority\":\"HIGH\",\"dueDate\":null}";
        mvc.perform(put("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        // Update Labels
        String labelsBody = "{\"labels\":[\"urgent\", \"backend\"]}";
        mvc.perform(put("/api/tasks/" + taskId + "/labels")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(labelsBody))
                .andExpect(status().isOk());

        // Update Focus
        String focusBody = "{\"focus\":true}";
        mvc.perform(put("/api/tasks/" + taskId + "/focus")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(focusBody))
                .andExpect(status().isOk());

        // Archive
        String archiveBody = "{\"archived\":true}";
        mvc.perform(put("/api/tasks/" + taskId + "/archive")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(archiveBody))
                .andExpect(status().isOk());

        // Delete
        mvc.perform(delete("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
    }

    @Test
    void dependency_updates_work() throws Exception {
        String token = registerAndLogin("dep@test.com", "Pass123!");
        JsonNode t1 = createTask(token, "Task 1");
        JsonNode t2 = createTask(token, "Task 2");
        String t1Id = t1.get("id").asText();
        String t2Id = t2.get("id").asText();

        // T2 blocked by T1
        String depBody = "{\"blockedByTaskIds\":[\"" + t1Id + "\"]}";
        mvc.perform(put("/api/tasks/" + t2Id + "/dependencies")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(depBody))
                .andExpect(status().isOk());
    }

    @Test
    void checklist_management_works() throws Exception {
        String token = registerAndLogin("check@test.com", "Pass123!");
        JsonNode task = createTask(token, "Checklist Task");
        String taskId = task.get("id").asText();

        // Add Item
        mvc.perform(post("/api/tasks/" + taskId + "/checklist")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"Item 1\"}"))
                .andExpect(status().isOk());

        // Get Task to find Item ID
        String json = mvc.perform(get("/api/tasks/" + taskId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode updated = objectMapper.readTree(json);
        String itemId = updated.get("checklist").get(0).get("id").asText();

        // Update Item
        String updateBody = "{\"text\":\"Updated Item 1\",\"done\":true}";
        mvc.perform(put("/api/tasks/" + taskId + "/checklist/" + itemId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody))
                .andExpect(status().isOk());

        // Reorder (trivial with 1 item but hits the endpoint)
        String reorderBody = "{\"itemIds\":[\"" + itemId + "\"]}";
        mvc.perform(post("/api/tasks/" + taskId + "/checklist/reorder")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reorderBody))
                .andExpect(status().isOk());
    }

    @Test
    void comments_and_decisions_work() throws Exception {
        String token = registerAndLogin("comm@test.com", "Pass123!");
        JsonNode task = createTask(token, "Discussion Task");
        String taskId = task.get("id").asText();

        // Add Comment
        mvc.perform(post("/api/tasks/" + taskId + "/comments")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"This is a comment\"}"))
                .andExpect(status().isOk());

        // Add Decision
        mvc.perform(post("/api/tasks/" + taskId + "/decisions")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"This is a decision\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void timers_work() throws Exception {
        String token = registerAndLogin("timer@test.com", "Pass123!");
        JsonNode task = createTask(token, "Timer Task");
        String taskId = task.get("id").asText();

        // Start Timer
        mvc.perform(post("/api/tasks/" + taskId + "/timer/start")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        // Stop Timer
        String stopBody = "{\"note\":\"Finished work\"}";
        mvc.perform(post("/api/tasks/" + taskId + "/timer/stop")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(stopBody))
                .andExpect(status().isOk());
    }

    @Test
    void bulk_actions_comprehensive_work() throws Exception {
        String token = registerAndLogin("bulk2@test.com", "Pass123!");
        JsonNode t1 = createTask(token, "Bulk 1");
        JsonNode t2 = createTask(token, "Bulk 2");
        String id1 = t1.get("id").asText();
        String id2 = t2.get("id").asText();
        String ids = "[\"" + id1 + "\", \"" + id2 + "\"]";

        // 1. SET_PRIORITY
        mvc.perform(post("/api/tasks/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SET_PRIORITY\",\"taskIds\":" + ids + ", \"priority\":\"HIGH\"}"))
                .andExpect(status().isOk());

        // 2. SET_DUE_DATE
        mvc.perform(post("/api/tasks/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SET_DUE_DATE\",\"taskIds\":" + ids + ", \"dueDate\":\"2026-01-01\"}"))
                .andExpect(status().isOk());

        // 3. ADD_LABEL
        mvc.perform(post("/api/tasks/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"ADD_LABEL\",\"taskIds\":" + ids + ", \"label\":\"bulk-tag\"}"))
                .andExpect(status().isOk());

        // 4. SET_FOCUS
        mvc.perform(post("/api/tasks/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SET_FOCUS\",\"taskIds\":" + ids + ", \"focus\":true}"))
                .andExpect(status().isOk());

        // 5. REMOVE_LABEL
        mvc.perform(post("/api/tasks/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"REMOVE_LABEL\",\"taskIds\":" + ids + ", \"label\":\"bulk-tag\"}"))
                .andExpect(status().isOk());
        
        // 6. SET_STATUS (DONE)
        mvc.perform(post("/api/tasks/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"SET_STATUS\",\"taskIds\":" + ids + ", \"status\":\"DONE\"}"))
                .andExpect(status().isOk());

        // 7. DELETE
        mvc.perform(post("/api/tasks/bulk")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"action\":\"DELETE\",\"taskIds\":" + ids + "}"))
                .andExpect(status().isOk());
        
        // Verify deletion
        mvc.perform(get("/api/tasks/" + id1)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void search_works() throws Exception {
        String token = registerAndLogin("search@test.com", "Pass123!");
        createTask(token, "Alpha Task");
        createTask(token, "Beta Task");

        mvc.perform(get("/api/tasks/search?q=Alpha")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void createTask_emptyTitle_fails() throws Exception {
        String token = registerAndLogin("val@test.com", "Pass123!");
        String body = objectMapper.writeValueAsString(new CreateBody("", "desc", "MEDIUM")); // empty title

        mvc.perform(post("/api/tasks")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateTask_missingId_fails() throws Exception {
        String token = registerAndLogin("missing@test.com", "Pass123!");
        mvc.perform(get("/api/tasks/non-existent-id")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private String registerAndLogin() throws Exception {
        return registerAndLogin("user2@example.com", "Password123!");
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
