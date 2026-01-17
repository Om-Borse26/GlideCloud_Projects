package com.glideclouds.taskmanagementsystem.tasks.dto;

import java.time.Instant;

public record ChecklistItemResponse(
        String id,
        String text,
        boolean done,
        int position,
        Instant createdAt
) {
}
