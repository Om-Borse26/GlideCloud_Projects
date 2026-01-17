package com.glideclouds.taskmanagementsystem.tasks.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateFocusRequest(
        @NotNull Boolean focus
) {
}
