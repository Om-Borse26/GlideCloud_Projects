package com.glideclouds.taskmanagementsystem.tasks.dto;

import java.time.Instant;

public record TaskCommentResponse(
        String id,
        String authorUserId,
        String authorEmail,
        String message,
        Instant createdAt
) {
}
