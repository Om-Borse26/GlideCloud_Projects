package com.glideclouds.taskmanagementsystem.tasks.dto;

import java.time.Instant;

public record TaskDecisionResponse(
        String id,
        String authorUserId,
        String authorEmail,
        String message,
        Instant createdAt
) {
}
