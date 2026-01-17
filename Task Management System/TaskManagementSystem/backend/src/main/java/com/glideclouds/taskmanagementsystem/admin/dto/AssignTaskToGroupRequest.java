package com.glideclouds.taskmanagementsystem.admin.dto;

import com.glideclouds.taskmanagementsystem.tasks.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record AssignTaskToGroupRequest(
        @NotBlank String groupId,
        @NotBlank @Size(max = 120) String title,
        @Size(max = 2000) String description,
        @NotNull TaskPriority priority,
        LocalDate dueDate
) {
}
