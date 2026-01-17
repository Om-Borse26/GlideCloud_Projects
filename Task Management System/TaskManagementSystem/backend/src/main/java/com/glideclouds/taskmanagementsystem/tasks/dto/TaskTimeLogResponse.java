package com.glideclouds.taskmanagementsystem.tasks.dto;

import java.time.Instant;

public record TaskTimeLogResponse(
        String id,
        Instant startedAt,
        Instant endedAt,
        long durationMinutes,
        String note,
        Instant createdAt
) {
}
