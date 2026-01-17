package com.glideclouds.taskmanagementsystem.tasks.dto;

import com.glideclouds.taskmanagementsystem.tasks.TaskPriority;
import com.glideclouds.taskmanagementsystem.tasks.TaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;

public record BulkTaskActionRequest(
        @NotNull List<String> taskIds,
        @NotBlank String action,
        TaskPriority priority,
        LocalDate dueDate,
        String label,
        TaskStatus status,
        Boolean focus
) {
}
