package com.glideclouds.taskmanagementsystem.ai.dto;

import jakarta.validation.constraints.NotBlank;

public record AiTemplateRequest(
        @NotBlank String taskId,
        String prompt
) {
}
