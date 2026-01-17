package com.glideclouds.taskmanagementsystem.tasks.dto;

import java.util.List;

public record SearchTasksResponse(
        List<TaskResponse> results
) {
}
