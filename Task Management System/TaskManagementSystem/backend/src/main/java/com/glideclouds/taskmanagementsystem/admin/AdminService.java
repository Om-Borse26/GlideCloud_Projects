package com.glideclouds.taskmanagementsystem.admin;

import com.glideclouds.taskmanagementsystem.admin.dto.*;
import com.glideclouds.taskmanagementsystem.admin.groups.Group;
import com.glideclouds.taskmanagementsystem.admin.groups.GroupRepository;
import com.glideclouds.taskmanagementsystem.notifications.NotificationService;
import com.glideclouds.taskmanagementsystem.tasks.*;
import com.glideclouds.taskmanagementsystem.users.User;
import com.glideclouds.taskmanagementsystem.users.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.springframework.http.HttpStatus.*;

@Service
public class AdminService {

    private final UserRepository userRepository;
    private final TaskRepository taskRepository;
    private final GroupRepository groupRepository;
    private final NotificationService notificationService;

    public AdminService(UserRepository userRepository,
                        TaskRepository taskRepository,
                        GroupRepository groupRepository,
                        NotificationService notificationService) {
        this.userRepository = userRepository;
        this.taskRepository = taskRepository;
        this.groupRepository = groupRepository;
        this.notificationService = notificationService;
    }

    public GroupResponse createGroup(String adminUserId, CreateGroupRequest request) {
        String name = request.name().trim();
        if (groupRepository.existsByName(name)) {
            throw new ResponseStatusException(CONFLICT, "Group name already exists");
        }

        List<String> memberUserIds = resolveEmailsToUserIds(request.memberEmails());
        Group group = new Group(name, memberUserIds, adminUserId);
        Group saved = groupRepository.save(group);

        return toGroupResponse(saved);
    }

    public List<GroupResponse> listGroups() {
        return groupRepository.findAll().stream().map(this::toGroupResponse).toList();
    }

    public GroupResponse updateGroupMembers(String groupId, UpdateGroupRequest request) {
        Group group = groupRepository.findById(groupId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));

        group.setMemberUserIds(resolveEmailsToUserIds(request.memberEmails()));
        Group saved = groupRepository.save(group);

        return toGroupResponse(saved);
    }

    public Task assignTaskToUser(String adminUserId, AssignTaskToUserRequest request) {
        User assignee = userRepository.findByEmail(request.assigneeEmail().trim().toLowerCase())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Assignee not found"));

        Task created = createAssignedTask(adminUserId, assignee.getId(), request.title(), request.description(), request.priority(), request.dueDate());
        notificationService.taskAssigned(assignee, created);
        return created;
    }

    public List<Task> assignTaskToGroup(String adminUserId, AssignTaskToGroupRequest request) {
        Group group = groupRepository.findById(request.groupId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Group not found"));

        Map<String, User> usersById = userRepository.findAllById(group.getMemberUserIds())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));

        List<Task> created = new ArrayList<>();
        for (String userId : group.getMemberUserIds()) {
            Task t = createAssignedTask(adminUserId, userId, request.title(), request.description(), request.priority(), request.dueDate());
            User assignee = usersById.get(userId);
            if (assignee != null) {
                notificationService.taskAssigned(assignee, t);
            }
            created.add(t);
        }
        return created;
    }

    public List<AdminTaskResponse> listAllTasks() {
        List<Task> tasks = taskRepository.findAll();
        Map<String, String> emailById = userRepository.findAllById(
                        tasks.stream()
                                .flatMap(t -> Arrays.stream(new String[]{t.getOwnerUserId(), t.getCreatedByUserId()}))
                                .filter(Objects::nonNull)
                                .collect(Collectors.toSet())
                ).stream().collect(Collectors.toMap(User::getId, User::getEmail));

        tasks.sort(Comparator.comparing(Task::getStatus)
                .thenComparing(Task::isPinned, Comparator.reverseOrder())
                .thenComparingInt(Task::getPosition));

        return tasks.stream().map(t -> {
            boolean assigned = t.getCreatedByUserId() != null && t.getOwnerUserId() != null && !t.getCreatedByUserId().equals(t.getOwnerUserId());
            return new AdminTaskResponse(
                    t.getId(),
                    t.getTitle(),
                    t.getDescription(),
                    t.getStatus(),
                    t.getPriority(),
                    t.getDueDate(),
                    assigned,
                    t.isPinned(),
                    emailById.getOrDefault(t.getOwnerUserId(), ""),
                    emailById.getOrDefault(t.getCreatedByUserId(), ""),
                    t.getCreatedAt(),
                    t.getUpdatedAt()
            );
        }).toList();
    }

    private Task createAssignedTask(String adminUserId,
                                   String assigneeUserId,
                                   String title,
                                   String description,
                                   TaskPriority priority,
                                   java.time.LocalDate dueDate) {
        int nextPosition = nextPositionFor(assigneeUserId, TaskStatus.TODO, true);

        Task task = new Task();
        task.setOwnerUserId(assigneeUserId);
        task.setCreatedByUserId(adminUserId);
        task.setTitle(title);
        task.setDescription(description);
        task.setPriority(priority);
        task.setDueDate(dueDate);
        task.setStatus(TaskStatus.TODO);
        task.setPinned(true);
        task.setPosition(nextPosition);

        TaskActivity a = new TaskActivity();
        a.setId(UUID.randomUUID().toString());
        a.setType(TaskActivityType.ASSIGNED);
        a.setActorUserId(adminUserId);
        a.setActorEmail("");
        a.setCreatedAt(Instant.now());
        a.setMessage("Task assigned");
        a.setFromStatus(null);
        a.setToStatus(TaskStatus.TODO);
        task.getActivity().add(a);

        return taskRepository.save(task);
    }

    private int nextPositionFor(String userId, TaskStatus status, boolean pinned) {
        return taskRepository.findByOwnerUserIdAndStatusOrderByPositionAsc(userId, status)
                .stream()
                .filter(t -> t.isPinned() == pinned)
                .mapToInt(Task::getPosition)
                .max()
                .orElse(-1) + 1;
    }

    private GroupResponse toGroupResponse(Group group) {
        Map<String, String> emailById = userRepository.findAllById(new HashSet<>(group.getMemberUserIds()))
                .stream()
                .collect(Collectors.toMap(User::getId, User::getEmail));

        List<String> memberEmails = group.getMemberUserIds().stream()
                .map(id -> emailById.getOrDefault(id, ""))
                .filter(s -> !s.isBlank())
                .sorted()
                .toList();

        return new GroupResponse(group.getId(), group.getName(), memberEmails, group.getCreatedAt(), group.getUpdatedAt());
    }

    private List<String> resolveEmailsToUserIds(List<String> emails) {
        if (emails == null || emails.isEmpty()) {
            return List.of();
        }

        List<String> normalized = emails.stream()
                .filter(Objects::nonNull)
                .map(s -> s.trim().toLowerCase())
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();

        Map<String, String> idByEmail = userRepository.findAll().stream()
                .collect(Collectors.toMap(User::getEmail, User::getId, (a, b) -> a));

        List<String> missing = normalized.stream().filter(e -> !idByEmail.containsKey(e)).toList();
        if (!missing.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Unknown users: " + String.join(", ", missing));
        }

        return normalized.stream().map(idByEmail::get).toList();
    }
}
