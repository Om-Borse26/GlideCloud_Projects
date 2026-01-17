package com.glideclouds.taskmanagementsystem.tasks.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReorderChecklistRequest(
        @NotNull List<String> itemIds
) {
}
