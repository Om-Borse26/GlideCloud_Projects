package com.glideclouds.taskmanagementsystem.ai;

import com.glideclouds.taskmanagementsystem.ai.dto.AiStatusResponse;
import com.glideclouds.taskmanagementsystem.ai.dto.AiTemplateResponse;
import com.glideclouds.taskmanagementsystem.tasks.Task;
import com.glideclouds.taskmanagementsystem.tasks.TaskRepository;
import com.glideclouds.taskmanagementsystem.tasks.TaskStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.http.HttpStatus.FORBIDDEN;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class AiService {

    private final AiProperties properties;
    private final TaskRepository taskRepository;

    public AiService(AiProperties properties, TaskRepository taskRepository) {
        this.properties = properties;
        this.taskRepository = taskRepository;
    }

    public AiStatusResponse status() {
        boolean enabled = properties.enabled();
        String provider = properties.provider() == null ? "" : properties.provider();
        boolean hasKey = properties.apiKey() != null && !properties.apiKey().trim().isBlank();

        String mode = (enabled && hasKey) ? "enabled" : "template";
        String message;
        if (!enabled) {
            message = "AI integration is in template mode. Set app.ai.enabled=true and configure app.ai.api-key to enable.";
        } else if (!hasKey) {
            message = "AI enabled but missing API key. Configure app.ai.api-key (server-side) to enable provider calls.";
        } else {
            message = "AI integration enabled (provider configured server-side).";
        }

        return new AiStatusResponse(enabled, provider, mode, message);
    }

    public AiTemplateResponse taskAssistTemplate(String userId, boolean isAdmin, String taskId, String prompt) {
        Task task = taskRepository.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Task not found"));
        if (!isAdmin && (task.getOwnerUserId() == null || !task.getOwnerUserId().equals(userId))) {
            throw new ResponseStatusException(FORBIDDEN, "Forbidden");
        }

        // Template-only response for now. Later, we can route to a provider client.
        StringBuilder sb = new StringBuilder();
        sb.append(task.getTitle() == null ? "(untitled)" : task.getTitle());
        sb.append("\n");
        sb.append("Status: ").append(task.getStatus() == null ? "" : task.getStatus());
        if (task.getPriority() != null) {
            sb.append(" • Priority: ").append(task.getPriority());
        }
        if (task.getDueDate() != null) {
            sb.append(" • Due: ").append(task.getDueDate());
        }
        sb.append("\n");

        if (task.getLabels() != null && !task.getLabels().isEmpty()) {
            sb.append("Labels: ");
            sb.append(String.join(", ", task.getLabels().stream().limit(8).toList()));
            if (task.getLabels().size() > 8) sb.append("…");
            sb.append("\n");
        }

        int totalChecklist = task.getChecklist() == null ? 0 : task.getChecklist().size();
        int doneChecklist = (int) (task.getChecklist() == null ? 0 : task.getChecklist().stream().filter(i -> i != null && i.isDone()).count());
        if (totalChecklist > 0) {
            int pct = (int) Math.round((doneChecklist * 100.0) / Math.max(1, totalChecklist));
            sb.append("Checklist: ").append(doneChecklist).append("/").append(totalChecklist).append(" (").append(pct).append("%)\n");
        }

        List<String> actions = new ArrayList<>();
        if (task.getDescription() == null || task.getDescription().trim().length() < 10) {
            actions.add("Add a clearer description / acceptance criteria");
        }
        if (task.getDueDate() == null) {
            actions.add("Set a due date");
        } else if (task.getStatus() != TaskStatus.DONE) {
            LocalDate today = LocalDate.now();
            if (task.getDueDate().isBefore(today)) {
                actions.add("Reschedule or reprioritize (overdue)");
            }
        }
        if (task.getTimeBudgetMinutes() == null) {
            actions.add("Set a time budget (minutes)");
        }
        if (!task.isFocus() && task.getStatus() != TaskStatus.DONE) {
            actions.add("Consider marking as Today’s focus");
        }
        if (task.getBlockedByTaskIds() != null && !task.getBlockedByTaskIds().isEmpty()) {
            actions.add("Check blockers and unblock dependencies");
        }

        AiStatusResponse st = status();
        String message = "template".equalsIgnoreCase(st.mode())
            ? "Template response only (no external AI calls yet)."
            : "AI is enabled, but provider calls are not wired yet.";

        return new AiTemplateResponse(st.enabled(), st.mode(), sb.toString(), actions.stream().limit(6).toList(), message);
    }
}
