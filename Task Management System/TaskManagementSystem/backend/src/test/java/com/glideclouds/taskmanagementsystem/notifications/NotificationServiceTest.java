package com.glideclouds.taskmanagementsystem.notifications;

import com.glideclouds.taskmanagementsystem.config.ClientProperties;
import com.glideclouds.taskmanagementsystem.tasks.Task;
import com.glideclouds.taskmanagementsystem.tasks.TaskPriority; // Correct import
import com.glideclouds.taskmanagementsystem.users.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private EmailService emailService;

    @Mock
    private ClientProperties clientProperties;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(emailService, clientProperties);
    }

    @Test
    void taskAssigned_sendsEmail_whenAssigneeAndTaskArePresent() {
        when(clientProperties.baseUrl()).thenReturn("http://localhost:5173");

        User assignee = new User();
        assignee.setEmail("user@example.com");

        Task task = new Task();
        task.setTitle("Test Task");
        task.setPriority(TaskPriority.HIGH); // Use enum constant
        task.setDueDate(LocalDate.of(2023, 10, 25));

        notificationService.taskAssigned(assignee, task);

        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("user@example.com"), eq("New task assigned: Test Task"), bodyCaptor.capture());

        String body = bodyCaptor.getValue();
        assertThat(body).contains("Title: Test Task");
        assertThat(body).contains("Priority: HIGH");
        assertThat(body).contains("Due date: 2023-10-25");
        assertThat(body).contains("Open your board: http://localhost:5173/board");
    }

    @Test
    void taskAssigned_doesNothing_whenAssigneeIsNull() {
        notificationService.taskAssigned(null, new Task());
        verifyNoInteractions(emailService);
    }

    @Test
    void taskAssigned_doesNothing_whenTaskIsNull() {
        notificationService.taskAssigned(new User(), null);
        verifyNoInteractions(emailService);
    }
}
