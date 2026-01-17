package com.glideclouds.taskmanagementsystem.ai.dto;

public record AiStatusResponse(
        boolean enabled,
        String provider,
        String mode,
        String message
) {
}
