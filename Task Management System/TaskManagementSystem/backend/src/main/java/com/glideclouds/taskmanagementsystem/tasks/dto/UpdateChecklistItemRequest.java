package com.glideclouds.taskmanagementsystem.tasks.dto;

import jakarta.validation.constraints.Size;

public record UpdateChecklistItemRequest(
        @Size(max = 120) String text,
        Boolean done
) {
}
