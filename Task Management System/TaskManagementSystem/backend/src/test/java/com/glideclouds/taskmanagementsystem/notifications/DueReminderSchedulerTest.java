package com.glideclouds.taskmanagementsystem.notifications;

import com.glideclouds.taskmanagementsystem.config.ClientProperties;
import com.glideclouds.taskmanagementsystem.tasks.Task;
import com.glideclouds.taskmanagementsystem.tasks.TaskRepository;
import com.glideclouds.taskmanagementsystem.tasks.TaskStatus;
import com.glideclouds.taskmanagementsystem.users.User;
import com.glideclouds.taskmanagementsystem.users.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DueReminderSchedulerTest {

    @Mock private EmailService emailService;
    @Mock private TaskRepository taskRepository;
    @Mock private UserRepository userRepository;
    @Mock private ClientProperties clientProperties;

    private DueReminderScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new DueReminderScheduler(emailService, taskRepository, userRepository, clientProperties);
    }

    @Test
    void sendDailyDueReminders_doesNothing_whenNoTasksDue() {
        when(taskRepository.findByDueDateAndStatusNot(any(LocalDate.class), eq(TaskStatus.DONE)))
                .thenReturn(Collections.emptyList());
        when(taskRepository.findByDueDateBeforeAndStatusNot(any(LocalDate.class), eq(TaskStatus.DONE)))
                .thenReturn(Collections.emptyList());

        scheduler.sendDailyDueReminders();

        verifyNoInteractions(emailService);
        verifyNoInteractions(userRepository);
    }

    @Test
    void sendDailyDueReminders_sendsEmails_whenTasksAreDue() {
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        Task t1 = new Task(); t1.setTitle("Due Today"); t1.setDueDate(today); t1.setOwnerUserId("u1");
        Task t2 = new Task(); t2.setTitle("Due Tomorrow"); t2.setDueDate(tomorrow); t2.setOwnerUserId("u1");
        Task t3 = new Task(); t3.setTitle("Overdue"); t3.setDueDate(today.minusDays(1)); t3.setOwnerUserId("u2");

        when(taskRepository.findByDueDateAndStatusNot(eq(today), eq(TaskStatus.DONE))).thenReturn(List.of(t1));
        when(taskRepository.findByDueDateAndStatusNot(eq(tomorrow), eq(TaskStatus.DONE))).thenReturn(List.of(t2));
        when(taskRepository.findByDueDateBeforeAndStatusNot(eq(today), eq(TaskStatus.DONE))).thenReturn(List.of(t3));

        User u1 = new User(); u1.setId("u1"); u1.setEmail("u1@test.com");
        User u2 = new User(); u2.setId("u2"); u2.setEmail("u2@test.com");

        when(userRepository.findAllById(any(Set.class))).thenReturn(List.of(u1, u2));
        when(clientProperties.baseUrl()).thenReturn("http://app");

        scheduler.sendDailyDueReminders();

        verify(emailService, times(2)).send(anyString(), anyString(), anyString());

        // Verify u1 email
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).send(eq("u1@test.com"), eq("Task reminders"), bodyCaptor.capture());
        String body1 = bodyCaptor.getValue();
        assertThat(body1).contains("Due today:");
        assertThat(body1).contains("Due Tomorrow");
        assertThat(body1).doesNotContain("Overdue:"); // u1 has no overdue

        // Verify u2 email relies on another capture or knowing mockito orders
        // Simpler to just verify generic send happened for u2
         verify(emailService).send(eq("u2@test.com"), eq("Task reminders"), anyString());
    }
}
