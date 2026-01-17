package com.glideclouds.taskmanagementsystem.tasks.dto;

import com.glideclouds.taskmanagementsystem.tasks.TaskActivityType;
import com.glideclouds.taskmanagementsystem.tasks.TaskStatus;

import java.time.Instant;

public record TaskActivityResponse(
        String id,
        TaskActivityType type,
        String actorUserId,
        String actorEmail,
        Instant createdAt,
        String message,
        TaskStatus fromStatus,
        TaskStatus toStatus
) {
}
