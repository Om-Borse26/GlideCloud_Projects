package com.glideclouds.taskmanagementsystem.admin;

import com.glideclouds.taskmanagementsystem.admin.dto.*;
import com.glideclouds.taskmanagementsystem.security.SecurityUtils;
import com.glideclouds.taskmanagementsystem.tasks.Task;
import com.glideclouds.taskmanagementsystem.tasks.TaskMapper;
import com.glideclouds.taskmanagementsystem.tasks.TaskRepository;
import com.glideclouds.taskmanagementsystem.tasks.dto.TaskResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin", description = "Administration operations")
@SecurityRequirement(name = "bearerAuth")
public class AdminController {

    private final AdminService adminService;
    private final TaskRepository taskRepository;

    public AdminController(AdminService adminService, TaskRepository taskRepository) {
        this.adminService = adminService;
        this.taskRepository = taskRepository;
    }

    @GetMapping("/tasks")
    @Operation(summary = "Get all tasks", description = "Lists all tasks in the system.")
    public List<AdminTaskResponse> allTasks() {
        return adminService.listAllTasks();
    }

    @GetMapping("/users")
    @Operation(summary = "List all users (Debug)", description = "Lists all registered users for debugging persistence.")
    public List<com.glideclouds.taskmanagementsystem.users.User> allUsers() {
        return adminService.listAllUsers();
    }

    @GetMapping("/tasks/{id}")
    @Operation(summary = "Get task details", description = "Get details of a specific task.")
    public TaskResponse taskDetails(@PathVariable String id) {
        Task t = taskRepository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
        return TaskMapper.toResponse(t);
    }

    @PostMapping("/groups")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create group", description = "Creates a new user group.")
    public GroupResponse createGroup(@Valid @RequestBody CreateGroupRequest request) {
        String adminUserId = requireUserId();
        return adminService.createGroup(adminUserId, request);
    }

    @GetMapping("/groups")
    @Operation(summary = "List groups", description = "Lists all user groups.")
    public List<GroupResponse> listGroups() {
        return adminService.listGroups();
    }

    @PutMapping("/groups/{id}")
    @Operation(summary = "Update group", description = "Updates group members.")
    public GroupResponse updateGroup(@PathVariable String id, @Valid @RequestBody UpdateGroupRequest request) {
        return adminService.updateGroupMembers(id, request);
    }

    @PostMapping("/tasks/assign/user")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Assign task to user", description = "Assigns a task to a specific user.")
    public TaskResponse assignToUser(@Valid @RequestBody AssignTaskToUserRequest request) {
        String adminUserId = requireUserId();
        Task created = adminService.assignTaskToUser(adminUserId, request);
        return TaskMapper.toResponse(created);
    }

    @PostMapping("/tasks/assign/group")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Assign task to group", description = "Assigns a task to a group.")
    public List<TaskResponse> assignToGroup(@Valid @RequestBody AssignTaskToGroupRequest request) {
        String adminUserId = requireUserId();
        return adminService.assignTaskToGroup(adminUserId, request)
                .stream()
                .map(TaskMapper::toResponse)
                .toList();
    }

    private String requireUserId() {
        String userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return userId;
    }
}
