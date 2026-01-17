package com.glideclouds.taskmanagementsystem.tasks.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddDecisionRequest(
        @NotBlank @Size(max = 2000) String message
) {
}
