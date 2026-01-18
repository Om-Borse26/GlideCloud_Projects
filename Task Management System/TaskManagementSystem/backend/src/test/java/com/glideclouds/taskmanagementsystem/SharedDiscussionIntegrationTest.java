package com.glideclouds.taskmanagementsystem;

import com.glideclouds.taskmanagementsystem.tasks.*;
import com.glideclouds.taskmanagementsystem.tasks.dto.TaskResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SharedDiscussionIntegrationTest extends AbstractMongoIntegrationTest {

    @Autowired
    TaskRepository taskRepository;

    @Autowired
    TaskDiscussionRepository taskDiscussionRepository;

    @Autowired
    TaskService taskService;

    @Test
    void groupAssignedTasksShareCommentsAndDecisionsAcrossAssignees() {
        String discussionId = "disc-1";
        taskDiscussionRepository.save(new TaskDiscussion(discussionId));

        Task t1 = new Task();
        t1.setOwnerUserId("user-1");
        t1.setCreatedByUserId("admin-1");
        t1.setTitle("Shared Task");
        t1.setStatus(TaskStatus.TODO);
        t1.setPriority(TaskPriority.MEDIUM);
        t1.setPinned(true);
        t1.setPosition(0);
        t1.setSharedDiscussionId(discussionId);

        Task t2 = new Task();
        t2.setOwnerUserId("user-2");
        t2.setCreatedByUserId("admin-1");
        t2.setTitle("Shared Task");
        t2.setStatus(TaskStatus.TODO);
        t2.setPriority(TaskPriority.MEDIUM);
        t2.setPinned(true);
        t2.setPosition(0);
        t2.setSharedDiscussionId(discussionId);

        t1 = taskRepository.save(t1);
        t2 = taskRepository.save(t2);

        TaskResponse afterComment = taskService.addComment("user-1", "u1@example.com", false, t1.getId(), "hello team");
        assertThat(afterComment.comments()).extracting(c -> c.message()).contains("hello team");

        TaskResponse asUser2 = taskService.getForUser("user-2", t2.getId());
        assertThat(asUser2.comments()).extracting(c -> c.message()).contains("hello team");

        TaskResponse afterDecision = taskService.addDecision("user-2", "u2@example.com", false, t2.getId(), "ship it");
        assertThat(afterDecision.decisions()).extracting(d -> d.message()).contains("ship it");

        TaskResponse asUser1 = taskService.getForUser("user-1", t1.getId());
        assertThat(asUser1.decisions()).extracting(d -> d.message()).contains("ship it");
    }
}
