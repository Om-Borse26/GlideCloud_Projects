package com.glideclouds.taskmanagementsystem.notifications;

import com.glideclouds.taskmanagementsystem.config.ClientProperties;
import com.glideclouds.taskmanagementsystem.tasks.Task;
import com.glideclouds.taskmanagementsystem.tasks.TaskRepository;
import com.glideclouds.taskmanagementsystem.tasks.TaskStatus;
import com.glideclouds.taskmanagementsystem.users.User;
import com.glideclouds.taskmanagementsystem.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class DueReminderScheduler {

    private static final Logger log = LoggerFactory.getLogger(DueReminderScheduler.class);

    private final EmailService emailService;
    private final TaskRepository taskRepository;
    private final UserRepository userRepository;
    private final ClientProperties clientProperties;

    public DueReminderScheduler(EmailService emailService,
                               TaskRepository taskRepository,
                               UserRepository userRepository,
                               ClientProperties clientProperties) {
        this.emailService = emailService;
        this.taskRepository = taskRepository;
        this.userRepository = userRepository;
        this.clientProperties = clientProperties;
    }

    // Daily at 08:00 server local time
    @Scheduled(cron = "0 0 8 * * *")
    public void sendDailyDueReminders() {
        // EmailService no-ops if disabled.
        LocalDate today = LocalDate.now();
        LocalDate tomorrow = today.plusDays(1);

        List<Task> dueToday = taskRepository.findByDueDateAndStatusNot(today, TaskStatus.DONE);
        List<Task> dueTomorrow = taskRepository.findByDueDateAndStatusNot(tomorrow, TaskStatus.DONE);
        List<Task> overdue = taskRepository.findByDueDateBeforeAndStatusNot(today, TaskStatus.DONE);

        if (dueToday.isEmpty() && dueTomorrow.isEmpty() && overdue.isEmpty()) {
            return;
        }

        Map<String, List<Task>> dueTodayByUser = groupByOwner(dueToday);
        Map<String, List<Task>> dueTomorrowByUser = groupByOwner(dueTomorrow);
        Map<String, List<Task>> overdueByUser = groupByOwner(overdue);

        Set<String> userIds = new HashSet<>();
        userIds.addAll(dueTodayByUser.keySet());
        userIds.addAll(dueTomorrowByUser.keySet());
        userIds.addAll(overdueByUser.keySet());

        Map<String, String> emailById = userRepository.findAllById(userIds)
                .stream()
                .collect(Collectors.toMap(User::getId, User::getEmail));

        for (String userId : userIds) {
            String to = emailById.get(userId);
            if (to == null || to.isBlank()) {
                continue;
            }

            String subject = "Task reminders";
            String body = buildBody(today,
                    overdueByUser.getOrDefault(userId, List.of()),
                    dueTodayByUser.getOrDefault(userId, List.of()),
                    dueTomorrowByUser.getOrDefault(userId, List.of()),
                    clientProperties.baseUrl());

            emailService.send(to, subject, body);
        }

        log.info("Daily reminders processed (users={})", userIds.size());
    }

    private static Map<String, List<Task>> groupByOwner(List<Task> tasks) {
        Map<String, List<Task>> by = new HashMap<>();
        for (Task t : tasks) {
            if (t.getOwnerUserId() == null) continue;
            by.computeIfAbsent(t.getOwnerUserId(), k -> new ArrayList<>()).add(t);
        }
        return by;
    }

    private static String buildBody(LocalDate today,
                                    List<Task> overdue,
                                    List<Task> dueToday,
                                    List<Task> dueTomorrow,
                                    String baseUrl) {
        StringBuilder sb = new StringBuilder();
        sb.append("Here are your task reminders for ").append(today).append(".\n\n");

        if (!overdue.isEmpty()) {
            sb.append("Overdue:\n");
            for (Task t : overdue) {
                sb.append("- ").append(safe(t.getTitle())).append(" (due ").append(t.getDueDate()).append(")\n");
            }
            sb.append("\n");
        }

        if (!dueToday.isEmpty()) {
            sb.append("Due today:\n");
            for (Task t : dueToday) {
                sb.append("- ").append(safe(t.getTitle())).append("\n");
            }
            sb.append("\n");
        }

        if (!dueTomorrow.isEmpty()) {
            sb.append("Due tomorrow:\n");
            for (Task t : dueTomorrow) {
                sb.append("- ").append(safe(t.getTitle())).append("\n");
            }
            sb.append("\n");
        }

        String link = (baseUrl == null ? "" : baseUrl) + "/board";
        sb.append("Open your board to take action: ").append(link);
        return sb.toString();
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
