package com.glideclouds.taskmanagementsystem.tasks.dto;

import com.glideclouds.taskmanagementsystem.tasks.TaskPriority;
import com.glideclouds.taskmanagementsystem.tasks.TaskStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record TaskResponse(
        String id,
        String title,
        String description,
        TaskStatus status,
        TaskPriority priority,
        LocalDate dueDate,
        int position,
        boolean assigned,
        boolean pinned,
        boolean archived,
        Instant archivedAt,
        List<String> labels,
        List<String> blockedByTaskIds,
        List<ChecklistItemResponse> checklist,
        int checklistDone,
        int checklistTotal,
        RecurrenceRuleResponse recurrence,
        List<TaskDecisionResponse> decisions,
        boolean focus,
        Integer timeBudgetMinutes,
        long totalLoggedMinutes,
        List<TaskTimeLogResponse> timeLogs,
        Instant activeTimerStartedAt,
        List<TaskCommentResponse> comments,
        List<TaskActivityResponse> activity,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
