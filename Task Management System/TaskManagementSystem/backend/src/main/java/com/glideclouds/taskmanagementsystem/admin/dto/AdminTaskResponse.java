package com.glideclouds.taskmanagementsystem.admin.dto;

import com.glideclouds.taskmanagementsystem.tasks.TaskPriority;
import com.glideclouds.taskmanagementsystem.tasks.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;

public record AdminTaskResponse(
        String id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        boolean assigned,
        boolean pinned,
        String ownerEmail,
        String createdByEmail,
        Instant createdAt,
        Instant updatedAt
) {
}
