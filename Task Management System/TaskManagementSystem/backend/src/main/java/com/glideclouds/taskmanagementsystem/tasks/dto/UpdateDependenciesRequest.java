package com.glideclouds.taskmanagementsystem.tasks.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateDependenciesRequest(
        @Size(max = 20) List<@Size(max = 80) String> blockedByTaskIds
) {
}
