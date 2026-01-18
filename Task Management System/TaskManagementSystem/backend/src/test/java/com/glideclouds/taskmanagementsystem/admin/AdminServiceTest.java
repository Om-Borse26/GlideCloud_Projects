package com.glideclouds.taskmanagementsystem.admin;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.glideclouds.taskmanagementsystem.admin.dto.AssignTaskToUserRequest;
import com.glideclouds.taskmanagementsystem.admin.groups.GroupRepository;
import com.glideclouds.taskmanagementsystem.notifications.NotificationService;
import com.glideclouds.taskmanagementsystem.tasks.Task;
import com.glideclouds.taskmanagementsystem.tasks.TaskActivityType;
import com.glideclouds.taskmanagementsystem.tasks.TaskPriority;
import com.glideclouds.taskmanagementsystem.tasks.TaskDiscussionRepository;
import com.glideclouds.taskmanagementsystem.tasks.TaskRepository;
import com.glideclouds.taskmanagementsystem.tasks.TaskStatus;
import com.glideclouds.taskmanagementsystem.users.User;
import com.glideclouds.taskmanagementsystem.users.UserRepository;

class AdminServiceTest {

    @Test
    void assignTaskToUser_createsPinnedTask_andSendsNotification() {
        UserRepository userRepository = mock(UserRepository.class);
        TaskRepository taskRepository = mock(TaskRepository.class);
        GroupRepository groupRepository = mock(GroupRepository.class);
        NotificationService notificationService = mock(NotificationService.class);
        TaskDiscussionRepository taskDiscussionRepository = mock(TaskDiscussionRepository.class);

        AdminService service = new AdminService(userRepository, taskRepository, groupRepository, notificationService, taskDiscussionRepository);

        User assignee = new User("user@example.com", "hash", com.glideclouds.taskmanagementsystem.users.Role.USER);
        assignee.setId("assignee-1");

        when(userRepository.findByEmail("user@example.com")).thenReturn(Optional.of(assignee));
        when(taskRepository.findByOwnerUserIdAndStatusOrderByPositionAsc("assignee-1", TaskStatus.TODO)).thenReturn(List.of());
        when(taskRepository.save(any(Task.class))).thenAnswer(inv -> inv.getArgument(0));

        AssignTaskToUserRequest req = new AssignTaskToUserRequest(
                "user@example.com",
                "Title",
                "Desc",
                TaskPriority.MEDIUM,
                null
        );

        Task created = service.assignTaskToUser("admin-1", req);

        assertThat(created.getOwnerUserId()).isEqualTo("assignee-1");
        assertThat(created.getCreatedByUserId()).isEqualTo("admin-1");
        assertThat(created.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(created.isPinned()).isTrue();
        assertThat(created.getActivity()).isNotNull();
        assertThat(created.getActivity()).isNotEmpty();
        assertThat(created.getActivity().getFirst().getType()).isEqualTo(TaskActivityType.ASSIGNED);

        verify(notificationService, times(1)).taskAssigned(eq(assignee), any(Task.class));
    }
}
