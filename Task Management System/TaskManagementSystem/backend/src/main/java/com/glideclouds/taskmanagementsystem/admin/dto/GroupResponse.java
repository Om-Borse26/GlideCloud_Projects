package com.glideclouds.taskmanagementsystem.admin.dto;

import java.time.Instant;
import java.util.List;

public record GroupResponse(
        String id,
        String name,
        List<String> memberEmails,
        Instant createdAt,
        Instant updatedAt
) {
}
