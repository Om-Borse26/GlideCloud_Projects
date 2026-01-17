package com.glideclouds.taskmanagementsystem.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddChecklistItemRequest(
        @NotBlank @Size(max = 120) String text
) {
}
