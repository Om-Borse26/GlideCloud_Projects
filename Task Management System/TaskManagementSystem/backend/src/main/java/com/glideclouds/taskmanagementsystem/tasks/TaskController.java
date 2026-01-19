package com.glideclouds.taskmanagementsystem.tasks;

import com.glideclouds.taskmanagementsystem.security.SecurityUtils;
import com.glideclouds.taskmanagementsystem.tasks.dto.CreateTaskRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.AddCommentRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.AddChecklistItemRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.AddDecisionRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.BulkTaskActionRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.MoveTaskRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.ReorderChecklistRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.TaskResponse;
import com.glideclouds.taskmanagementsystem.tasks.dto.TimerNoteRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateTaskRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateChecklistItemRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateLabelsRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateFocusRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateTimeBudgetRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateRecurrenceRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateDependenciesRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateArchivedRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks", description = "Operations related to managing tasks")
@SecurityRequirement(name = "bearerAuth")
public class TaskController {

    private final TaskService taskService;

    public TaskController(TaskService taskService) {
        this.taskService = taskService;
    }

    @GetMapping
    @Operation(summary = "List tasks", description = "Lists tasks for the current user (board-ready ordering).")
    public List<TaskResponse> list() {
        String userId = requireUserId();
        return taskService.listForUser(userId);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a task", description = "Fetches a single task by id for the current user.")
    public TaskResponse getOne(@PathVariable String id) {
        String userId = requireUserId();
        return taskService.getForUser(userId, id);
    }

    @GetMapping("/search")
    @Operation(summary = "Search tasks", description = "Search for tasks by query string.")
    public List<TaskResponse> search(@RequestParam(name = "q", required = false) String q) {
        String userId = requireUserId();
        return taskService.searchForUser(userId, q);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create task", description = "Creates a new task for the current user.")
    public TaskResponse create(@Valid @RequestBody CreateTaskRequest request) {
        String userId = requireUserId();
        return taskService.createForUser(userId, request);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update task", description = "Updates an existing task.")
    public TaskResponse update(@PathVariable String id, @Valid @RequestBody UpdateTaskRequest request) {
        String userId = requireUserId();
        return taskService.updateForUser(userId, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete task", description = "Deletes a task by ID.")
    public void delete(@PathVariable String id) {
        String userId = requireUserId();
        taskService.deleteForUser(userId, id);
    }

    @PostMapping("/move")
    @Operation(summary = "Move task", description = "Moves a task across columns and reindexes affected columns.")
    public List<TaskResponse> move(@Valid @RequestBody MoveTaskRequest request) {
        String userId = requireUserId();
        return taskService.moveForUser(userId, request);
    }

    @PostMapping("/{id}/comments")
    @Operation(summary = "Add comment", description = "Adds a comment to a task.")
    public TaskResponse addComment(@PathVariable String id, @Valid @RequestBody AddCommentRequest request) {
        String userId = requireUserId();
        String email = SecurityUtils.currentUserEmail();
        boolean isAdmin = SecurityUtils.currentHasRole("ADMIN");
        return taskService.addComment(userId, email, isAdmin, id, request.message());
    }

    @PutMapping("/{id}/labels")
    @Operation(summary = "Update labels", description = "Updates labels for a task.")
    public TaskResponse updateLabels(@PathVariable String id, @Valid @RequestBody UpdateLabelsRequest request) {
        String userId = requireUserId();
        return taskService.updateLabelsForUser(userId, id, request.labels());
    }

    @PutMapping("/{id}/focus")
    @Operation(summary = "Update focus", description = "Updates focus status of a task.")
    public TaskResponse updateFocus(@PathVariable String id, @Valid @RequestBody UpdateFocusRequest request) {
        String userId = requireUserId();
        return taskService.updateFocusForUser(userId, id, request);
    }

    @PutMapping("/{id}/time-budget")
    @Operation(summary = "Update time budget", description = "Updates the time budget for a task.")
    public TaskResponse updateTimeBudget(@PathVariable String id, @Valid @RequestBody UpdateTimeBudgetRequest request) {
        String userId = requireUserId();
        return taskService.updateTimeBudgetForUser(userId, id, request);
    }

    @PutMapping("/{id}/recurrence")
    @Operation(summary = "Update recurrence", description = "Updates recurrence settings for a task.")
    public TaskResponse updateRecurrence(@PathVariable String id, @Valid @RequestBody(required = false) UpdateRecurrenceRequest request) {
        String userId = requireUserId();
        return taskService.updateRecurrenceForUser(userId, id, request);
    }

    @PutMapping("/{id}/dependencies")
    @Operation(summary = "Update dependencies", description = "Updates dependencies for a task.")
    public TaskResponse updateDependencies(@PathVariable String id, @Valid @RequestBody(required = false) UpdateDependenciesRequest request) {
        String userId = requireUserId();
        return taskService.updateDependenciesForUser(userId, id, request);
    }

    @PutMapping("/{id}/archive")
    @Operation(summary = "Archive/Unarchive task", description = "Archives or unarchives a task.")
    public TaskResponse updateArchived(@PathVariable String id, @Valid @RequestBody UpdateArchivedRequest request) {
        String userId = requireUserId();
        return taskService.updateArchivedForUser(userId, id, request);
    }

    @PostMapping("/{id}/checklist")
    @Operation(summary = "Add checklist item", description = "Adds an item to the task checklist.")
    public TaskResponse addChecklistItem(@PathVariable String id, @Valid @RequestBody AddChecklistItemRequest request) {
        String userId = requireUserId();
        return taskService.addChecklistItemForUser(userId, id, request.text());
    }

    @PutMapping("/{id}/checklist/{itemId}")
    @Operation(summary = "Update checklist item", description = "Updates a checklist item.")
    public TaskResponse updateChecklistItem(@PathVariable String id, @PathVariable String itemId, @Valid @RequestBody UpdateChecklistItemRequest request) {
        String userId = requireUserId();
        return taskService.updateChecklistItemForUser(userId, id, itemId, request);
    }

    @PostMapping("/{id}/checklist/reorder")
    @Operation(summary = "Reorder checklist", description = "Reorders checklist items.")
    public TaskResponse reorderChecklist(@PathVariable String id, @Valid @RequestBody ReorderChecklistRequest request) {
        String userId = requireUserId();
        return taskService.reorderChecklistForUser(userId, id, request);
    }

    @PostMapping("/{id}/decisions")
    @Operation(summary = "Add decision", description = "Adds a decision to the task.")
    public TaskResponse addDecision(@PathVariable String id, @Valid @RequestBody AddDecisionRequest request) {
        String userId = requireUserId();
        String email = SecurityUtils.currentUserEmail();
        boolean isAdmin = SecurityUtils.currentHasRole("ADMIN");
        return taskService.addDecision(userId, email, isAdmin, id, request.message());
    }

    @PostMapping("/{id}/timer/start")
    @Operation(summary = "Start timer", description = "Starts a timer for the task.")
    public TaskResponse startTimer(@PathVariable String id, @Valid @RequestBody(required = false) TimerNoteRequest request) {
        String userId = requireUserId();
        return taskService.startTimerForUser(userId, id, request);
    }

    @PostMapping("/{id}/timer/stop")
    @Operation(summary = "Stop timer", description = "Stops the timer for the task.")
    public TaskResponse stopTimer(@PathVariable String id, @Valid @RequestBody(required = false) TimerNoteRequest request) {
        String userId = requireUserId();
        return taskService.stopTimerForUser(userId, id, request);
    }

    @PostMapping("/bulk")
    @Operation(summary = "Bulk actions", description = "Performs bulk actions on tasks.")
    public List<TaskResponse> bulk(@Valid @RequestBody BulkTaskActionRequest request) {
        String userId = requireUserId();
        return taskService.bulkForUser(userId, request);
    }

    private String requireUserId() {
        String userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return userId;
    }
}
