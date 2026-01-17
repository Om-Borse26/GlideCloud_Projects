package com.glideclouds.taskmanagementsystem.notifications;

import com.glideclouds.taskmanagementsystem.config.ClientProperties;
import com.glideclouds.taskmanagementsystem.tasks.Task;
import com.glideclouds.taskmanagementsystem.users.User;
import org.springframework.stereotype.Service;

import java.time.format.DateTimeFormatter;

@Service
public class NotificationService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE;

    private final EmailService emailService;
    private final ClientProperties clientProperties;

    public NotificationService(EmailService emailService, ClientProperties clientProperties) {
        this.emailService = emailService;
        this.clientProperties = clientProperties;
    }

    public void taskAssigned(User assignee, Task task) {
        if (assignee == null || task == null) {
            return;
        }

        String subject = "New task assigned: " + safe(task.getTitle());

        String due = task.getDueDate() == null ? "—" : DATE_FMT.format(task.getDueDate());
        String prio = task.getPriority() == null ? "MEDIUM" : task.getPriority().name();
        String link = (clientProperties.baseUrl() == null ? "" : clientProperties.baseUrl()) + "/board";

        String body = "You have been assigned a new task.\n\n"
                + "Title: " + safe(task.getTitle()) + "\n"
                + "Priority: " + prio + "\n"
                + "Due date: " + due + "\n\n"
                + "Open your board: " + link + "\n";

        emailService.send(assignee.getEmail(), subject, body);
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }
}
