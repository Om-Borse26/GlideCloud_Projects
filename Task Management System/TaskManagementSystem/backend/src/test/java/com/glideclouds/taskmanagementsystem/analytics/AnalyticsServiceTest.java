package com.glideclouds.taskmanagementsystem.analytics;

import com.glideclouds.taskmanagementsystem.analytics.dto.AnalyticsOverviewResponse;
import com.glideclouds.taskmanagementsystem.tasks.Task;
import com.glideclouds.taskmanagementsystem.tasks.TaskRepository;
import com.glideclouds.taskmanagementsystem.tasks.TaskStatus;
import com.glideclouds.taskmanagementsystem.tasks.TaskTimeLog;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalyticsServiceTest {

    @Test
    void overview_countsDueOverdueCompletedAndLoggedMinutes() throws Exception {
        TaskRepository repo = mock(TaskRepository.class);
        AnalyticsService service = new AnalyticsService(repo);

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);

        Task overdue = new Task();
        overdue.setId("t1");
        overdue.setOwnerUserId("u1");
        overdue.setStatus(TaskStatus.TODO);
        overdue.setDueDate(today.minusDays(1));
        overdue.setLabels(List.of("Work"));

        Task dueToday = new Task();
        dueToday.setId("t2");
        dueToday.setOwnerUserId("u1");
        dueToday.setStatus(TaskStatus.IN_PROGRESS);
        dueToday.setDueDate(today);
        dueToday.setFocus(true);
        dueToday.setLabels(List.of("work", "Urgent"));

        Task doneToday = new Task();
        doneToday.setId("t3");
        doneToday.setOwnerUserId("u1");
        doneToday.setStatus(TaskStatus.DONE);
        setCreatedAt(doneToday, Instant.now().minusSeconds(3600));
        doneToday.setCompletedAt(Instant.now());

        Task withLog = new Task();
        withLog.setId("t4");
        withLog.setOwnerUserId("u1");
        withLog.setStatus(TaskStatus.TODO);
        TaskTimeLog log = new TaskTimeLog();
        log.setId("l1");
        log.setEndedAt(Instant.now());
        log.setDurationMinutes(25);
        withLog.setTimeLogs(List.of(log));

        when(repo.findByOwnerUserId("u1")).thenReturn(List.of(overdue, dueToday, doneToday, withLog));

        AnalyticsOverviewResponse r = service.overviewForUser("u1", 14);

        assertThat(r.totalTasks()).isEqualTo(4);
        assertThat(r.openTasks()).isEqualTo(3);
        assertThat(r.overdueOpenTasks()).isEqualTo(1);
        assertThat(r.dueTodayOpenTasks()).isEqualTo(1);
        assertThat(r.completedToday()).isEqualTo(1);
        assertThat(r.focusOpenTasks()).isEqualTo(1);
        assertThat(r.loggedMinutesToday()).isEqualTo(25);

        assertThat(r.topOpenLabels()).isNotEmpty();
        assertThat(r.statusCounts()).containsKeys("TODO", "IN_PROGRESS", "DONE");
        assertThat(r.trend()).hasSize(14);

        assertThat(r.completionStreakDays()).isGreaterThanOrEqualTo(0);
        assertThat(r.bottlenecks()).isNotNull();
    }

    private static void setCreatedAt(Task task, Instant createdAt) throws Exception {
        Field f = Task.class.getDeclaredField("createdAt");
        f.setAccessible(true);
        f.set(task, createdAt);
    }
}
