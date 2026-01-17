package com.glideclouds.taskmanagementsystem.tasks.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateLabelsRequest(
        @NotNull List<String> labels
) {
}
