package com.glideclouds.taskmanagementsystem.tasks;

import com.glideclouds.taskmanagementsystem.tasks.dto.*;

import java.util.Comparator;
import java.util.List;

public final class TaskMapper {

    private TaskMapper() {
    }

    public static TaskResponse toResponse(Task task) {
        boolean assigned = task.getCreatedByUserId() != null
                && task.getOwnerUserId() != null
                && !task.getCreatedByUserId().equals(task.getOwnerUserId());
        return toResponse(task, assigned);
    }

    public static TaskResponse toResponse(Task task, boolean assigned) {
        List<TaskCommentResponse> comments = (task.getComments() == null ? List.<TaskComment>of() : task.getComments())
                .stream()
                .sorted(Comparator.comparing(TaskComment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(c -> new TaskCommentResponse(c.getId(), c.getAuthorUserId(), c.getAuthorEmail(), c.getMessage(), c.getCreatedAt()))
                .toList();

        List<TaskActivityResponse> activity = (task.getActivity() == null ? List.<TaskActivity>of() : task.getActivity())
                .stream()
                .sorted(Comparator.comparing(TaskActivity::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(a -> new TaskActivityResponse(a.getId(), a.getType(), a.getActorUserId(), a.getActorEmail(), a.getCreatedAt(), a.getMessage(), a.getFromStatus(), a.getToStatus()))
                .toList();

        List<ChecklistItemResponse> checklist = (task.getChecklist() == null ? List.<ChecklistItem>of() : task.getChecklist())
                .stream()
                .sorted(Comparator.comparingInt(ChecklistItem::getPosition))
                .map(i -> new ChecklistItemResponse(i.getId(), i.getText(), i.isDone(), i.getPosition(), i.getCreatedAt()))
                .toList();

        int checklistTotal = checklist.size();
        int checklistDone = (int) checklist.stream().filter(ChecklistItemResponse::done).count();

        RecurrenceRuleResponse recurrence = task.getRecurrence() == null ? null : new RecurrenceRuleResponse(
                task.getRecurrence().getFrequency(),
                task.getRecurrence().getInterval(),
                task.getRecurrence().isWeekdaysOnly(),
                task.getRecurrence().getDaysOfWeek(),
                task.getRecurrence().getEndDate(),
                task.getRecurrence().getNthBusinessDayOfMonth()
        );

        List<TaskDecisionResponse> decisions = (task.getDecisions() == null ? List.<TaskDecision>of() : task.getDecisions())
                .stream()
                .sorted(Comparator.comparing(TaskDecision::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(d -> new TaskDecisionResponse(d.getId(), d.getAuthorUserId(), d.getAuthorEmail(), d.getMessage(), d.getCreatedAt()))
                .toList();

        List<TaskTimeLogResponse> timeLogs = (task.getTimeLogs() == null ? List.<TaskTimeLog>of() : task.getTimeLogs())
                .stream()
                .sorted(Comparator.comparing(TaskTimeLog::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(l -> new TaskTimeLogResponse(l.getId(), l.getStartedAt(), l.getEndedAt(), l.getDurationMinutes(), l.getNote(), l.getCreatedAt()))
                .toList();

        long totalLoggedMinutes = timeLogs.stream().mapToLong(TaskTimeLogResponse::durationMinutes).sum();

        return new TaskResponse(
                task.getId(),
                task.getTitle(),
                task.getDescription(),
                task.getStatus(),
                task.getPriority(),
                task.getDueDate(),
                task.getPosition(),
                assigned,
                task.isPinned(),
                task.isArchived(),
                task.getArchivedAt(),
                task.getLabels() == null ? List.of() : task.getLabels(),
                task.getBlockedByTaskIds() == null ? List.of() : task.getBlockedByTaskIds(),
                checklist,
                checklistDone,
                checklistTotal,
                recurrence,
                decisions,
                task.isFocus(),
                task.getTimeBudgetMinutes(),
                totalLoggedMinutes,
                timeLogs,
                task.getActiveTimerStartedAt(),
                comments,
                activity,
                task.getCompletedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }
}
