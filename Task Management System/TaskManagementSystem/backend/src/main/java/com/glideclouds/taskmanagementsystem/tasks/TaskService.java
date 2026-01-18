package com.glideclouds.taskmanagementsystem.tasks;

import com.glideclouds.taskmanagementsystem.tasks.dto.CreateTaskRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.BulkTaskActionRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.ReorderChecklistRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.MoveTaskRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.TaskResponse;
import com.glideclouds.taskmanagementsystem.tasks.dto.TimerNoteRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateTaskRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateChecklistItemRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateFocusRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateArchivedRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateRecurrenceRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateTimeBudgetRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateDependenciesRequest;
import com.glideclouds.taskmanagementsystem.security.SecurityUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@Service
/**
 * Core task domain service.
 * <p>
 * Owns per-user CRUD/mutations (comments, checklist, timers, recurrence, dependencies) and board ordering.
 * Also applies auto-archiving rules for DONE tasks when listing tasks.
 */
public class TaskService {

    private static final Logger log = LoggerFactory.getLogger(TaskService.class);

    private final TaskRepository taskRepository;
    private final TaskDiscussionRepository taskDiscussionRepository;

    @Value("${tasks.archive.done-after-days:1}")
    private long archiveDoneAfterDays;

    private static final int MAX_COMMENTS = 200;
    private static final int MAX_ACTIVITY = 400;
    private static final int MAX_DECISIONS = 200;
    private static final int MAX_TIME_LOGS = 400;
    private static final int MAX_CHECKLIST = 100;
    private static final int MAX_LABELS = 20;
    private static final int MAX_DEPENDENCIES = 20;

    public TaskService(TaskRepository taskRepository, TaskDiscussionRepository taskDiscussionRepository) {
        this.taskRepository = taskRepository;
        this.taskDiscussionRepository = taskDiscussionRepository;
    }

    /**
     * Lists tasks for a user (sorted for board rendering) and performs best-effort auto-archiving of stale DONE tasks.
     */
    public List<TaskResponse> listForUser(String userId) {
        List<Task> tasks = taskRepository.findByOwnerUserId(userId);
        maybeAutoArchiveDoneTasks(tasks);
        tasks.sort(taskComparator());
        return toResponsesWithSharedDiscussions(tasks);
    }

    /** Returns a single task, enforcing owner access. */
    public TaskResponse getForUser(String userId, String taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }
        return toResponseWithSharedDiscussion(task);
    }

    /** Archives/unarchives a task for the owner and maintains archivedAt consistently. */
    public TaskResponse updateArchivedForUser(String userId, String taskId, UpdateArchivedRequest request) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        boolean archived = request.archived();
        task.setArchived(archived);
        if (archived) {
            if (task.getArchivedAt() == null) {
                task.setArchivedAt(Instant.now());
            }
        } else {
            task.setArchivedAt(null);
        }
        appendActivity(task, TaskActivityType.UPDATED, userId, null, archived ? "Task archived" : "Task unarchived", null, null);
        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    /** Creates a new TODO task for the user at the end of the TODO column. */
    public TaskResponse createForUser(String userId, CreateTaskRequest request) {
        int nextPosition = nextPositionFor(userId, TaskStatus.TODO, false);

        Task task = new Task();
        task.setOwnerUserId(userId);
        task.setCreatedByUserId(userId);
        task.setTitle(request.title());
        task.setDescription(request.description());
        task.setPriority(request.priority() != null ? request.priority() : TaskPriority.MEDIUM);
        task.setDueDate(request.dueDate());
        task.setStatus(TaskStatus.TODO);
        task.setPosition(nextPosition);
        task.setPinned(false);

        appendActivity(task, TaskActivityType.CREATED, userId, null, "Task created", null, null);

        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    /** Updates editable fields on a user-owned task (title/description/priority/dueDate). */
    public TaskResponse updateForUser(String userId, String taskId, UpdateTaskRequest request) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        task.setTitle(request.title());
        task.setDescription(request.description());
        if (request.priority() != null) {
            task.setPriority(request.priority());
        }
        task.setDueDate(request.dueDate());

        appendActivity(task, TaskActivityType.UPDATED, userId, null, "Task updated", null, null);

        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    /** Adds a comment (owner/creator/admin) and appends activity. */
    public TaskResponse addComment(String userId, String userEmail, boolean isAdmin, String taskId, String message) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));

        boolean allowed = isAdmin
                || userId.equals(task.getOwnerUserId())
                || userId.equals(task.getCreatedByUserId());
        if (!allowed) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        String trimmed = message == null ? "" : message.trim();
        if (trimmed.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Message is required");
        }

        TaskComment c = new TaskComment();
        c.setId(UUID.randomUUID().toString());
        c.setAuthorUserId(userId);
        c.setAuthorEmail(userEmail == null ? "" : userEmail);
        c.setMessage(trimmed);
        c.setCreatedAt(Instant.now());

        if (task.getSharedDiscussionId() != null && !task.getSharedDiscussionId().isBlank()) {
            TaskDiscussion discussion = taskDiscussionRepository.findById(task.getSharedDiscussionId())
                    .orElseGet(() -> new TaskDiscussion(task.getSharedDiscussionId()));
            if (discussion.getComments() == null) {
                discussion.setComments(new ArrayList<>());
            }
            discussion.getComments().add(c);
            if (discussion.getComments().size() > MAX_COMMENTS) {
                discussion.setComments(discussion.getComments().subList(discussion.getComments().size() - MAX_COMMENTS, discussion.getComments().size()));
            }
            taskDiscussionRepository.save(discussion);
        } else {
            if (task.getComments() == null) {
                task.setComments(new ArrayList<>());
            }
            task.getComments().add(c);

            if (task.getComments().size() > MAX_COMMENTS) {
                task.setComments(task.getComments().subList(task.getComments().size() - MAX_COMMENTS, task.getComments().size()));
            }
        }

        appendActivity(task, TaskActivityType.COMMENTED, userId, userEmail, "Comment added", null, null);

        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    public void deleteForUser(String userId, String taskId) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        if (isAssignedFromAdmin(task)) {
            throw new ResponseStatusException(FORBIDDEN, "Assigned tasks cannot be deleted");
        }
        taskRepository.delete(task);

        // Keep positions consistent in the column after deletion
        reindexColumn(userId, task.getStatus());
    }

    public List<TaskResponse> searchForUser(String userId, String query) {
        String q = query == null ? "" : query.trim().toLowerCase();
        if (q.isBlank()) {
            return listForUser(userId);
        }

        return listForUser(userId)
                .stream()
                .filter(t -> {
                    String title = t.title() == null ? "" : t.title().toLowerCase();
                    String desc = t.description() == null ? "" : t.description().toLowerCase();
                    boolean labelMatch = (t.labels() == null ? List.<String>of() : t.labels())
                            .stream()
                            .anyMatch(l -> l != null && l.toLowerCase().contains(q));
                    boolean commentMatch = (t.comments() == null ? List.<com.glideclouds.taskmanagementsystem.tasks.dto.TaskCommentResponse>of() : t.comments())
                            .stream()
                            .anyMatch(c -> c != null && c.message() != null && c.message().toLowerCase().contains(q));
                    boolean decisionMatch = (t.decisions() == null ? List.<com.glideclouds.taskmanagementsystem.tasks.dto.TaskDecisionResponse>of() : t.decisions())
                            .stream()
                            .anyMatch(d -> d != null && d.message() != null && d.message().toLowerCase().contains(q));
                    return title.contains(q) || desc.contains(q) || labelMatch || commentMatch || decisionMatch;
                })
                .toList();
    }

    public TaskResponse updateFocusForUser(String userId, String taskId, UpdateFocusRequest request) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        boolean focus = request.focus();
        task.setFocus(focus);
        appendActivity(task, TaskActivityType.FOCUS_UPDATED, userId, null, focus ? "Marked as focus" : "Unmarked as focus", null, null);
        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    public TaskResponse updateTimeBudgetForUser(String userId, String taskId, UpdateTimeBudgetRequest request) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        Integer budget = request.timeBudgetMinutes();
        if (budget != null && budget < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "timeBudgetMinutes must be >= 0");
        }
        task.setTimeBudgetMinutes(budget);
        appendActivity(task, TaskActivityType.TIME_BUDGET_UPDATED, userId, null, "Time budget updated", null, null);
        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    public TaskResponse updateRecurrenceForUser(String userId, String taskId, UpdateRecurrenceRequest request) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        if (request.frequency() == null) {
            task.setRecurrence(null);
            appendActivity(task, TaskActivityType.RECURRENCE_UPDATED, userId, null, "Recurrence cleared", null, null);
            Task saved = taskRepository.save(task);
            return toResponseWithSharedDiscussion(saved);
        }

        RecurrenceRule rule = task.getRecurrence() == null ? new RecurrenceRule() : task.getRecurrence();
        rule.setFrequency(request.frequency());
        rule.setInterval(request.interval() == null ? 1 : Math.max(1, request.interval()));
        rule.setWeekdaysOnly(Boolean.TRUE.equals(request.weekdaysOnly()));
        rule.setDaysOfWeek(request.daysOfWeek() == null ? List.of() : request.daysOfWeek());
        rule.setEndDate(request.endDate());

        Integer nth = request.nthBusinessDayOfMonth();
        if (nth != null && nth <= 0) {
            nth = null;
        }
        rule.setNthBusinessDayOfMonth(nth);

        task.setRecurrence(rule);
        appendActivity(task, TaskActivityType.RECURRENCE_UPDATED, userId, null, "Recurrence updated", null, null);
        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    public TaskResponse updateDependenciesForUser(String userId, String taskId, UpdateDependenciesRequest request) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : (request.blockedByTaskIds() == null ? List.<String>of() : request.blockedByTaskIds())) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isBlank()) continue;
            if (s.equals(taskId)) continue;
            if (seen.add(s)) {
                cleaned.add(s);
            }
            if (cleaned.size() >= MAX_DEPENDENCIES) break;
        }

        if (!cleaned.isEmpty()) {
            List<Task> deps = taskRepository.findAllById(cleaned);
            if (deps.size() != cleaned.size()) {
                throw new ResponseStatusException(BAD_REQUEST, "One or more dependency tasks were not found");
            }
            boolean allOwnedBySameUser = deps.stream().allMatch(t -> task.getOwnerUserId() != null && task.getOwnerUserId().equals(t.getOwnerUserId()));
            if (!allOwnedBySameUser) {
                throw new ResponseStatusException(BAD_REQUEST, "Dependencies must belong to the same owner");
            }
        }

        task.setBlockedByTaskIds(cleaned);
        appendActivity(task, TaskActivityType.DEPENDENCIES_UPDATED, userId, null, "Dependencies updated", null, null);
        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    public TaskResponse updateLabelsForUser(String userId, String taskId, List<String> labels) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        List<String> cleaned = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String raw : labels == null ? List.<String>of() : labels) {
            if (raw == null) continue;
            String s = raw.trim();
            if (s.isBlank()) continue;
            if (s.length() > 24) {
                s = s.substring(0, 24);
            }
            String key = s.toLowerCase();
            if (seen.add(key)) {
                cleaned.add(s);
            }
            if (cleaned.size() >= MAX_LABELS) break;
        }

        task.setLabels(cleaned);
        appendActivity(task, TaskActivityType.LABELS_UPDATED, userId, null, "Labels updated", null, null);
        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    public TaskResponse addChecklistItemForUser(String userId, String taskId, String text) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        String trimmed = text == null ? "" : text.trim();
        if (trimmed.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Text is required");
        }

        if (task.getChecklist() == null) {
            task.setChecklist(new ArrayList<>());
        }
        if (task.getChecklist().size() >= MAX_CHECKLIST) {
            throw new ResponseStatusException(BAD_REQUEST, "Checklist limit reached");
        }

        int nextPos = task.getChecklist().stream().mapToInt(ChecklistItem::getPosition).max().orElse(-1) + 1;
        ChecklistItem item = new ChecklistItem();
        item.setId(UUID.randomUUID().toString());
        item.setText(trimmed);
        item.setDone(false);
        item.setPosition(nextPos);
        item.setCreatedAt(Instant.now());
        task.getChecklist().add(item);

        appendActivity(task, TaskActivityType.CHECKLIST_UPDATED, userId, null, "Checklist updated", null, null);
        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    public TaskResponse updateChecklistItemForUser(String userId, String taskId, String itemId, UpdateChecklistItemRequest request) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        if (task.getChecklist() == null || task.getChecklist().isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "Checklist item not found");
        }

        ChecklistItem item = task.getChecklist().stream()
                .filter(i -> i.getId() != null && i.getId().equals(itemId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Checklist item not found"));

        if (request.text() != null) {
            String trimmed = request.text().trim();
            if (trimmed.isBlank()) {
                throw new ResponseStatusException(BAD_REQUEST, "Text is required");
            }
            item.setText(trimmed);
        }
        if (request.done() != null) {
            item.setDone(request.done());
        }

        appendActivity(task, TaskActivityType.CHECKLIST_UPDATED, userId, null, "Checklist updated", null, null);
        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    public TaskResponse reorderChecklistForUser(String userId, String taskId, ReorderChecklistRequest request) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        if (task.getChecklist() == null) {
            task.setChecklist(new ArrayList<>());
        }

        Map<String, ChecklistItem> byId = new HashMap<>();
        for (ChecklistItem i : task.getChecklist()) {
            if (i.getId() != null) byId.put(i.getId(), i);
        }

        int pos = 0;
        Set<String> seen = new HashSet<>();
        for (String id : request.itemIds()) {
            if (id == null) continue;
            ChecklistItem i = byId.get(id);
            if (i == null) continue;
            if (!seen.add(id)) continue;
            i.setPosition(pos++);
        }

        // Append any missing items at the end (stable-ish)
        List<ChecklistItem> missing = task.getChecklist().stream()
            .sorted(Comparator.comparingInt(ChecklistItem::getPosition))
            .filter(i -> i.getId() != null && !seen.contains(i.getId()))
            .toList();
        for (ChecklistItem i : missing) {
            i.setPosition(pos++);
        }

        appendActivity(task, TaskActivityType.CHECKLIST_UPDATED, userId, null, "Checklist reordered", null, null);
        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    public TaskResponse addDecision(String userId, String userEmail, boolean isAdmin, String taskId, String message) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));

        boolean allowed = isAdmin
                || userId.equals(task.getOwnerUserId())
                || userId.equals(task.getCreatedByUserId());
        if (!allowed) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        String trimmed = message == null ? "" : message.trim();
        if (trimmed.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "Message is required");
        }

        TaskDecision d = new TaskDecision();
        d.setId(UUID.randomUUID().toString());
        d.setAuthorUserId(userId);
        d.setAuthorEmail(userEmail == null ? "" : userEmail);
        d.setMessage(trimmed);
        d.setCreatedAt(Instant.now());

        if (task.getSharedDiscussionId() != null && !task.getSharedDiscussionId().isBlank()) {
            TaskDiscussion discussion = taskDiscussionRepository.findById(task.getSharedDiscussionId())
                    .orElseGet(() -> new TaskDiscussion(task.getSharedDiscussionId()));
            if (discussion.getDecisions() == null) {
                discussion.setDecisions(new ArrayList<>());
            }
            discussion.getDecisions().add(d);
            if (discussion.getDecisions().size() > MAX_DECISIONS) {
                discussion.setDecisions(discussion.getDecisions().subList(discussion.getDecisions().size() - MAX_DECISIONS, discussion.getDecisions().size()));
            }
            taskDiscussionRepository.save(discussion);
        } else {
            if (task.getDecisions() == null) {
                task.setDecisions(new ArrayList<>());
            }
            task.getDecisions().add(d);

            if (task.getDecisions().size() > MAX_DECISIONS) {
                task.setDecisions(task.getDecisions().subList(task.getDecisions().size() - MAX_DECISIONS, task.getDecisions().size()));
            }
        }

        appendActivity(task, TaskActivityType.DECISION_ADDED, userId, userEmail, "Decision added", null, null);
        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    public TaskResponse startTimerForUser(String userId, String taskId, @org.springframework.lang.Nullable TimerNoteRequest request) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }
        if (task.getActiveTimerStartedAt() != null) {
            throw new ResponseStatusException(CONFLICT, "Timer already running");
        }
        task.setActiveTimerStartedAt(Instant.now());
        appendActivity(task, TaskActivityType.TIMER_STARTED, userId, null, "Timer started", null, null);
        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    public TaskResponse stopTimerForUser(String userId, String taskId, @org.springframework.lang.Nullable TimerNoteRequest request) {
        Task task = taskRepository.findById(taskId).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!userId.equals(task.getOwnerUserId())) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }
        if (task.getActiveTimerStartedAt() == null) {
            throw new ResponseStatusException(CONFLICT, "Timer is not running");
        }

        Instant startedAt = task.getActiveTimerStartedAt();
        Instant endedAt = Instant.now();
        long minutes = Math.max(1, Duration.between(startedAt, endedAt).toMinutes());

        TaskTimeLog log = new TaskTimeLog();
        log.setId(UUID.randomUUID().toString());
        log.setStartedAt(startedAt);
        log.setEndedAt(endedAt);
        log.setDurationMinutes(minutes);
        log.setNote(request == null ? null : request.note());
        log.setCreatedAt(Instant.now());

        if (task.getTimeLogs() == null) {
            task.setTimeLogs(new ArrayList<>());
        }
        task.getTimeLogs().add(log);
        if (task.getTimeLogs().size() > MAX_TIME_LOGS) {
            task.setTimeLogs(task.getTimeLogs().subList(task.getTimeLogs().size() - MAX_TIME_LOGS, task.getTimeLogs().size()));
        }

        task.setActiveTimerStartedAt(null);
        appendActivity(task, TaskActivityType.TIMER_STOPPED, userId, null, "Timer stopped", null, null);
        Task saved = taskRepository.save(task);
        return toResponseWithSharedDiscussion(saved);
    }

    private List<TaskResponse> toResponsesWithSharedDiscussions(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        Set<String> ids = tasks.stream()
                .map(Task::getSharedDiscussionId)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toSet());

        Map<String, TaskDiscussion> byId = ids.isEmpty()
                ? Map.of()
                : taskDiscussionRepository.findAllById(ids).stream().collect(Collectors.toMap(TaskDiscussion::getId, d -> d));

        return tasks.stream()
                .map(t -> {
                    TaskResponse base = TaskMapper.toResponse(t);
                    String discussionId = t.getSharedDiscussionId();
                    if (discussionId == null || discussionId.isBlank()) return base;
                    TaskDiscussion discussion = byId.get(discussionId);
                    if (discussion == null) return base;
                    return withSharedDiscussion(base, discussion);
                })
                .toList();
    }

    private TaskResponse toResponseWithSharedDiscussion(Task task) {
        TaskResponse base = TaskMapper.toResponse(task);
        String discussionId = task.getSharedDiscussionId();
        if (discussionId == null || discussionId.isBlank()) {
            return base;
        }
        TaskDiscussion discussion = taskDiscussionRepository.findById(discussionId).orElse(null);
        if (discussion == null) {
            return base;
        }
        return withSharedDiscussion(base, discussion);
    }

    private static TaskResponse withSharedDiscussion(TaskResponse base, TaskDiscussion discussion) {
        List<com.glideclouds.taskmanagementsystem.tasks.dto.TaskCommentResponse> comments = (discussion.getComments() == null ? List.<TaskComment>of() : discussion.getComments())
                .stream()
                .sorted(Comparator.comparing(TaskComment::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(c -> new com.glideclouds.taskmanagementsystem.tasks.dto.TaskCommentResponse(c.getId(), c.getAuthorUserId(), c.getAuthorEmail(), c.getMessage(), c.getCreatedAt()))
                .toList();

        List<com.glideclouds.taskmanagementsystem.tasks.dto.TaskDecisionResponse> decisions = (discussion.getDecisions() == null ? List.<TaskDecision>of() : discussion.getDecisions())
                .stream()
                .sorted(Comparator.comparing(TaskDecision::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(d -> new com.glideclouds.taskmanagementsystem.tasks.dto.TaskDecisionResponse(d.getId(), d.getAuthorUserId(), d.getAuthorEmail(), d.getMessage(), d.getCreatedAt()))
                .toList();

        return new com.glideclouds.taskmanagementsystem.tasks.dto.TaskResponse(
                base.id(),
                base.title(),
                base.description(),
                base.status(),
                base.priority(),
                base.dueDate(),
                base.position(),
                base.assigned(),
                base.pinned(),
                base.archived(),
                base.archivedAt(),
                base.labels(),
                base.blockedByTaskIds(),
                base.checklist(),
                base.checklistDone(),
                base.checklistTotal(),
                base.recurrence(),
                decisions,
                base.focus(),
                base.timeBudgetMinutes(),
                base.totalLoggedMinutes(),
                base.timeLogs(),
                base.activeTimerStartedAt(),
                comments,
                base.activity(),
                base.completedAt(),
                base.createdAt(),
                base.updatedAt()
        );
    }

    public List<TaskResponse> bulkForUser(String userId, BulkTaskActionRequest request) {
        String action = request.action().trim().toUpperCase();
        List<String> ids = request.taskIds();
        if (ids.isEmpty()) {
            return listForUser(userId);
        }

        List<Task> tasks = taskRepository.findAllById(ids);
        List<Task> owned = tasks.stream().filter(t -> userId.equals(t.getOwnerUserId())).toList();

        Set<TaskStatus> statusesToReindex = new HashSet<>();

        switch (action) {
            case "DELETE" -> {
                for (Task t : owned) {
                    if (isAssignedFromAdmin(t)) continue;
                    statusesToReindex.add(t.getStatus());
                    taskRepository.delete(t);
                }
            }
            case "SET_STATUS" -> {
                if (request.status() == null) {
                    throw new ResponseStatusException(BAD_REQUEST, "status is required");
                }
                TaskStatus target = request.status();
                for (Task t : owned) {
                    if (isAssignedFromAdmin(t)) continue;
                    TaskStatus from = t.getStatus();
                    if (from == target) continue;

                    statusesToReindex.add(from);
                    statusesToReindex.add(target);

                    t.setStatus(target);

                    if (target == TaskStatus.DONE && from != TaskStatus.DONE) {
                        t.setCompletedAt(Instant.now());
                    } else if (from == TaskStatus.DONE && target != TaskStatus.DONE) {
                        t.setCompletedAt(null);
                    }

                    // Put at end; reindexColumn will normalize.
                    t.setPosition(999_999);
                    appendActivity(t, TaskActivityType.MOVED, userId, null, "Bulk moved", from, target);
                }
                taskRepository.saveAll(owned);
            }
            case "SET_PRIORITY" -> {
                if (request.priority() == null) {
                    throw new ResponseStatusException(BAD_REQUEST, "priority is required");
                }
                for (Task t : owned) {
                    if (isAssignedFromAdmin(t)) continue;
                    t.setPriority(request.priority());
                    appendActivity(t, TaskActivityType.UPDATED, userId, null, "Priority updated", null, null);
                }
                taskRepository.saveAll(owned);
            }
            case "SET_DUE_DATE" -> {
                for (Task t : owned) {
                    if (isAssignedFromAdmin(t)) continue;
                    t.setDueDate(request.dueDate());
                    appendActivity(t, TaskActivityType.UPDATED, userId, null, "Due date updated", null, null);
                }
                taskRepository.saveAll(owned);
            }
            case "ADD_LABEL" -> {
                String label = request.label() == null ? "" : request.label().trim();
                if (label.isBlank()) {
                    throw new ResponseStatusException(BAD_REQUEST, "label is required");
                }
                for (Task t : owned) {
                    if (isAssignedFromAdmin(t)) continue;
                    if (t.getLabels() == null) t.setLabels(new ArrayList<>());
                    if (t.getLabels().stream().noneMatch(x -> x != null && x.equalsIgnoreCase(label))) {
                        if (t.getLabels().size() < MAX_LABELS) {
                            t.getLabels().add(label);
                        }
                    }
                    appendActivity(t, TaskActivityType.LABELS_UPDATED, userId, null, "Label added", null, null);
                }
                taskRepository.saveAll(owned);
            }
            case "REMOVE_LABEL" -> {
                String label = request.label() == null ? "" : request.label().trim();
                if (label.isBlank()) {
                    throw new ResponseStatusException(BAD_REQUEST, "label is required");
                }
                for (Task t : owned) {
                    if (isAssignedFromAdmin(t)) continue;
                    if (t.getLabels() != null) {
                        t.setLabels(t.getLabels().stream().filter(x -> x != null && !x.equalsIgnoreCase(label)).toList());
                    }
                    appendActivity(t, TaskActivityType.LABELS_UPDATED, userId, null, "Label removed", null, null);
                }
                taskRepository.saveAll(owned);
            }
            case "SET_FOCUS" -> {
                boolean focus = Boolean.TRUE.equals(request.focus());
                for (Task t : owned) {
                    if (isAssignedFromAdmin(t)) continue;
                    t.setFocus(focus);
                    appendActivity(t, TaskActivityType.UPDATED, userId, null, focus ? "Focus enabled" : "Focus disabled", null, null);
                }
                taskRepository.saveAll(owned);
            }
            default -> throw new ResponseStatusException(BAD_REQUEST, "Unsupported bulk action");
        }

        for (TaskStatus s : statusesToReindex) {
            reindexColumn(userId, s);
        }

        return listForUser(userId);
    }

    public List<TaskResponse> moveForUser(String userId, MoveTaskRequest request) {
        Task task = taskRepository.findById(request.taskId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));

        if (!userId.equals(task.getOwnerUserId())) {
            boolean isAdmin = SecurityUtils.currentHasRole("ADMIN");
            if (!isAdmin) {
                log.warn("Forbidden move attempt: taskId={}, userId={}, ownerUserId={}, from={}, to={}",
                        request.taskId(), userId, task.getOwnerUserId(), request.fromStatus(), request.toStatus());
                throw new ResponseStatusException(FORBIDDEN, "Forbidden");
            }
        }

        if (task.getStatus() != request.fromStatus()) {
            throw new ResponseStatusException(CONFLICT, "Task status changed; refresh and retry");
        }

        TaskStatus from = request.fromStatus();
        TaskStatus to = request.toStatus();

        boolean pinned = task.isPinned();

        if (from == to) {
            List<Task> column = columnFor(userId, from);
            reorderWithinColumn(column, task, request.toIndex(), pinned);
            appendActivity(task, TaskActivityType.REORDERED, userId, null, "Task reordered", from, to);
            taskRepository.saveAll(column);
            return listForUser(userId);
        }

        List<Task> fromCol = columnFor(userId, from);
        List<Task> toCol = columnFor(userId, to);

        fromCol.removeIf(t -> t.getId().equals(task.getId()));
        task.setStatus(to);

        if (to == TaskStatus.DONE && from != TaskStatus.DONE) {
            task.setCompletedAt(Instant.now());
            appendActivity(task, TaskActivityType.COMPLETED, userId, null, "Task completed", from, to);
        } else if (from == TaskStatus.DONE && to != TaskStatus.DONE) {
            task.setCompletedAt(null);
        }

        appendActivity(task, TaskActivityType.MOVED, userId, null, "Task moved", from, to);

        int idx = clampToSegmentIndex(toCol, pinned, request.toIndex());
        toCol = insertIntoSegment(toCol, task, idx, pinned);

        reindexSegments(fromCol);
        reindexSegments(toCol);

        taskRepository.saveAll(fromCol);
        taskRepository.saveAll(toCol);

        if (to == TaskStatus.DONE && from != TaskStatus.DONE) {
            maybeCreateNextRecurringInstance(userId, task);
        }

        return listForUser(userId);
    }

    private void maybeAutoArchiveDoneTasks(List<Task> tasks) {
        if (tasks == null || tasks.isEmpty()) return;
        if (archiveDoneAfterDays <= 0) return;

        Instant cutoff = Instant.now().minus(Duration.ofDays(archiveDoneAfterDays));
        List<Task> changed = new ArrayList<>();

        for (Task t : tasks) {
            if (t == null) continue;

            if (t.getStatus() != TaskStatus.DONE) continue;
            if (t.isArchived()) continue;

            Instant completedAt = t.getCompletedAt();
            if (completedAt == null) continue;

            // Archive when completedAt is older than (or equal to) cutoff.
            if (!completedAt.isAfter(cutoff)) {
                t.setArchived(true);
                if (t.getArchivedAt() == null) {
                    t.setArchivedAt(Instant.now());
                }
                changed.add(t);
            }
        }

        if (!changed.isEmpty()) {
            taskRepository.saveAll(changed);
        }
    }

    private void maybeCreateNextRecurringInstance(String userId, Task completedTask) {
        if (completedTask.getRecurrence() == null) {
            return;
        }

        LocalDate base = completedTask.getDueDate() != null ? completedTask.getDueDate() : LocalDate.now();
        LocalDate nextDue = RecurrenceCalculator.nextDueDate(base, completedTask.getRecurrence());
        if (nextDue == null) {
            return;
        }

        Task next = new Task();
        next.setOwnerUserId(userId);
        next.setCreatedByUserId(userId);
        next.setTitle(completedTask.getTitle());
        next.setDescription(completedTask.getDescription());
        next.setPriority(completedTask.getPriority());
        next.setDueDate(nextDue);
        next.setStatus(TaskStatus.TODO);
        next.setPosition(nextPositionFor(userId, TaskStatus.TODO, false));
        next.setPinned(false);

        next.setLabels(completedTask.getLabels() == null ? new ArrayList<>() : new ArrayList<>(completedTask.getLabels()));
        next.setRecurrence(completedTask.getRecurrence());

        if (completedTask.getChecklist() != null) {
            List<ChecklistItem> copy = new ArrayList<>();
            for (ChecklistItem item : completedTask.getChecklist()) {
                ChecklistItem ci = new ChecklistItem();
                ci.setId(UUID.randomUUID().toString());
                ci.setText(item.getText());
                ci.setDone(false);
                ci.setPosition(item.getPosition());
                ci.setCreatedAt(Instant.now());
                copy.add(ci);
            }
            next.setChecklist(copy);
        }

        appendActivity(next, TaskActivityType.CREATED, userId, null, "Recurring task created", null, null);

        taskRepository.save(next);
        appendActivity(completedTask, TaskActivityType.RECURRENCE_NEXT_CREATED, userId, null, "Next recurring instance created", null, null);
        taskRepository.save(completedTask);
    }

    private void reorderWithinColumn(List<Task> column, Task task, int toIndex, boolean pinnedSegment) {
        List<Task> segment = column.stream().filter(t -> t.isPinned() == pinnedSegment).collect(Collectors.toCollection(ArrayList::new));
        segment.removeIf(t -> t.getId().equals(task.getId()));

        int idx = clampToSegmentIndex(column, pinnedSegment, toIndex);
        idx = Math.max(0, Math.min(idx, segment.size()));
        segment.add(idx, task);

        List<Task> other = column.stream().filter(t -> t.isPinned() != pinnedSegment).collect(Collectors.toCollection(ArrayList::new));
        column.clear();
        if (pinnedSegment) {
            column.addAll(segment);
            column.addAll(other);
        } else {
            column.addAll(other);
            column.addAll(segment);
        }

        reindexSegments(column);
    }

    private void reindexColumn(String userId, TaskStatus status) {
        List<Task> column = columnFor(userId, status);
        reindexSegments(column);
        taskRepository.saveAll(column);
    }

    private void reindexSegments(List<Task> tasks) {
        int p = 0;
        int u = 0;
        for (Task t : tasks.stream().filter(Task::isPinned).toList()) {
            t.setPosition(p++);
        }
        for (Task t : tasks.stream().filter(t -> !t.isPinned()).toList()) {
            t.setPosition(u++);
        }
    }

    private List<Task> columnFor(String userId, TaskStatus status) {
        List<Task> column = new ArrayList<>(taskRepository.findByOwnerUserIdAndStatusOrderByPositionAsc(userId, status));
        column.sort(Comparator.comparing(Task::isPinned).reversed().thenComparingInt(Task::getPosition));
        return column;
    }

    private int nextPositionFor(String userId, TaskStatus status, boolean pinned) {
        return taskRepository.findByOwnerUserIdAndStatusOrderByPositionAsc(userId, status)
                .stream()
                .filter(t -> t.isPinned() == pinned)
                .mapToInt(Task::getPosition)
                .max()
                .orElse(-1) + 1;
    }

    private int clampToSegmentIndex(List<Task> column, boolean pinnedSegment, int combinedIndex) {
        int pinnedCount = (int) column.stream().filter(Task::isPinned).count();
        if (pinnedSegment) {
            return Math.max(0, Math.min(combinedIndex, pinnedCount));
        }
        return Math.max(0, combinedIndex - pinnedCount);
    }

    private List<Task> insertIntoSegment(List<Task> column, Task task, int segmentIndex, boolean pinnedSegment) {
        List<Task> pinned = column.stream().filter(Task::isPinned).collect(Collectors.toCollection(ArrayList::new));
        List<Task> unpinned = column.stream().filter(t -> !t.isPinned()).collect(Collectors.toCollection(ArrayList::new));

        if (pinnedSegment) {
            int idx = Math.max(0, Math.min(segmentIndex, pinned.size()));
            pinned.add(idx, task);
        } else {
            int idx = Math.max(0, Math.min(segmentIndex, unpinned.size()));
            unpinned.add(idx, task);
        }

        List<Task> out = new ArrayList<>(pinned.size() + unpinned.size());
        out.addAll(pinned);
        out.addAll(unpinned);
        return out;
    }

    private Comparator<Task> taskComparator() {
        return Comparator
                .comparing(Task::getStatus)
                .thenComparing(Task::isPinned, Comparator.reverseOrder())
                .thenComparingInt(Task::getPosition);
    }

    private static void appendActivity(Task task,
                                       TaskActivityType type,
                                       String actorUserId,
                                       String actorEmail,
                                       String message,
                                       TaskStatus fromStatus,
                                       TaskStatus toStatus) {
        if (task.getActivity() == null) {
            task.setActivity(new ArrayList<>());
        }

        TaskActivity a = new TaskActivity();
        a.setId(UUID.randomUUID().toString());
        a.setType(type);
        a.setActorUserId(actorUserId);
        a.setActorEmail(actorEmail == null ? "" : actorEmail);
        a.setCreatedAt(Instant.now());
        a.setMessage(message);
        a.setFromStatus(fromStatus);
        a.setToStatus(toStatus);

        task.getActivity().add(a);

        if (task.getActivity().size() > MAX_ACTIVITY) {
            task.setActivity(task.getActivity().subList(task.getActivity().size() - MAX_ACTIVITY, task.getActivity().size()));
        }
    }

    private boolean isAssignedFromAdmin(Task task) {
        if (task.getCreatedByUserId() == null || task.getOwnerUserId() == null) {
            return false;
        }
        return !task.getCreatedByUserId().equals(task.getOwnerUserId());
    }
}
