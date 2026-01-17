package com.glideclouds.taskmanagementsystem.ai.dto;

import java.util.List;

public record AiTemplateResponse(
        boolean enabled,
        String mode,
        String summary,
        List<String> suggestedActions,
        String message
) {
}
