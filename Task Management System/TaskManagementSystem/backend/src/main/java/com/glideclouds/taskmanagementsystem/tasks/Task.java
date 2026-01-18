package com.glideclouds.taskmanagementsystem.tasks;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "tasks")
public class Task {

    @Id
    private String id;

    private String title;
    private String description;
    private TaskStatus status;
    private TaskPriority priority;
    private LocalDate dueDate;
    private int position;

    /**
     * When true, this task is "pinned" above normal tasks within its column.
     * Used for admin-assigned tasks so they appear above user-created tasks.
     */
    private boolean pinned;

    private List<String> labels = new ArrayList<>();

    /**
     * IDs of tasks that must be completed before this task can proceed.
     */
    private List<String> blockedByTaskIds = new ArrayList<>();

    private List<ChecklistItem> checklist = new ArrayList<>();

    private RecurrenceRule recurrence;

    private List<TaskDecision> decisions = new ArrayList<>();

    private boolean focus;

    private Integer timeBudgetMinutes;

    private List<TaskTimeLog> timeLogs = new ArrayList<>();

    private Instant activeTimerStartedAt;

    private List<TaskComment> comments = new ArrayList<>();

    private List<TaskActivity> activity = new ArrayList<>();

    private Instant completedAt;

    /**
     * When true, this task is hidden from the main board by default.
     * Timeline/history should still show it.
     */
    private boolean archived;

    /**
     * When this task was archived (manual or auto).
     */
    private Instant archivedAt;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    private String ownerUserId;

    /**
     * The user who originally created this task.
     * For user-created tasks, this equals ownerUserId. For admin-assigned tasks, this is the admin's userId.
     */
    private String createdByUserId;

    /**
     * Optional shared discussion/thread id.
     * When present, comments + decisions are stored in a shared document so all assignees can see the same discussion.
     */
    private String sharedDiscussionId;

    public Task() {
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public TaskStatus getStatus() {
        return status;
    }

    public void setStatus(TaskStatus status) {
        this.status = status;
    }

    public TaskPriority getPriority() {
        return priority;
    }

    public void setPriority(TaskPriority priority) {
        this.priority = priority;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public boolean isPinned() {
        return pinned;
    }

    public void setPinned(boolean pinned) {
        this.pinned = pinned;
    }

    public List<TaskComment> getComments() {
        return comments;
    }

    public void setComments(List<TaskComment> comments) {
        this.comments = comments;
    }

    public List<TaskActivity> getActivity() {
        return activity;
    }

    public void setActivity(List<TaskActivity> activity) {
        this.activity = activity;
    }

    public List<String> getLabels() {
        return labels;
    }

    public void setLabels(List<String> labels) {
        this.labels = labels;
    }

    public List<String> getBlockedByTaskIds() {
        return blockedByTaskIds;
    }

    public void setBlockedByTaskIds(List<String> blockedByTaskIds) {
        this.blockedByTaskIds = blockedByTaskIds;
    }

    public List<ChecklistItem> getChecklist() {
        return checklist;
    }

    public void setChecklist(List<ChecklistItem> checklist) {
        this.checklist = checklist;
    }

    public RecurrenceRule getRecurrence() {
        return recurrence;
    }

    public void setRecurrence(RecurrenceRule recurrence) {
        this.recurrence = recurrence;
    }

    public List<TaskDecision> getDecisions() {
        return decisions;
    }

    public void setDecisions(List<TaskDecision> decisions) {
        this.decisions = decisions;
    }

    public boolean isFocus() {
        return focus;
    }

    public void setFocus(boolean focus) {
        this.focus = focus;
    }

    public Integer getTimeBudgetMinutes() {
        return timeBudgetMinutes;
    }

    public void setTimeBudgetMinutes(Integer timeBudgetMinutes) {
        this.timeBudgetMinutes = timeBudgetMinutes;
    }

    public List<TaskTimeLog> getTimeLogs() {
        return timeLogs;
    }

    public void setTimeLogs(List<TaskTimeLog> timeLogs) {
        this.timeLogs = timeLogs;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }

    public Instant getActiveTimerStartedAt() {
        return activeTimerStartedAt;
    }

    public void setActiveTimerStartedAt(Instant activeTimerStartedAt) {
        this.activeTimerStartedAt = activeTimerStartedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public boolean isArchived() {
        return archived;
    }

    public void setArchived(boolean archived) {
        this.archived = archived;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(String createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getSharedDiscussionId() {
        return sharedDiscussionId;
    }

    public void setSharedDiscussionId(String sharedDiscussionId) {
        this.sharedDiscussionId = sharedDiscussionId;
    }
}
