package com.glideclouds.taskmanagementsystem.tasks.dto;

import com.glideclouds.taskmanagementsystem.tasks.TaskStatus;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record MoveTaskRequest(
        @NotBlank String taskId,
        @NotNull TaskStatus fromStatus,
        @NotNull TaskStatus toStatus,
        @Min(0) int toIndex
) {
}
