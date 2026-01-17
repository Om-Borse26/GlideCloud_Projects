package com.glideclouds.taskmanagementsystem.tasks;

import com.glideclouds.taskmanagementsystem.tasks.dto.MoveTaskRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateArchivedRequest;
import com.glideclouds.taskmanagementsystem.tasks.dto.UpdateDependenciesRequest;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class TaskServiceTest {

    @Test
    void updateArchived_archivesAndSetsArchivedAt() {
        TaskRepository repo = mock(TaskRepository.class);
        TaskService service = new TaskService(repo);

        String userId = "u1";
        Task t = task("t1", userId, TaskStatus.TODO, 0);
        t.setArchived(false);
        t.setArchivedAt(null);

        when(repo.findById("t1")).thenReturn(Optional.of(t));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var res = service.updateArchivedForUser(userId, "t1", new UpdateArchivedRequest(true));

        assertThat(res.archived()).isTrue();
        assertThat(res.archivedAt()).isNotNull();
        verify(repo).save(eq(t));
    }

    @Test
    void updateArchived_unarchivesAndClearsArchivedAt() {
        TaskRepository repo = mock(TaskRepository.class);
        TaskService service = new TaskService(repo);

        String userId = "u1";
        Task t = task("t1", userId, TaskStatus.DONE, 0);
        t.setArchived(true);
        t.setArchivedAt(Instant.now());

        when(repo.findById("t1")).thenReturn(Optional.of(t));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        var res = service.updateArchivedForUser(userId, "t1", new UpdateArchivedRequest(false));

        assertThat(res.archived()).isFalse();
        assertThat(res.archivedAt()).isNull();
        verify(repo).save(eq(t));
    }

    @Test
    void listForUser_autoArchivesDoneTasksOlderThanCutoff() {
        TaskRepository repo = mock(TaskRepository.class);
        TaskService service = new TaskService(repo);
        ReflectionTestUtils.setField(service, "archiveDoneAfterDays", 1L);

        String userId = "u1";

        Task doneOld = task("t-done-old", userId, TaskStatus.DONE, 0);
        doneOld.setArchived(false);
        doneOld.setArchivedAt(null);
        doneOld.setCompletedAt(Instant.now().minusSeconds(2 * 24 * 60 * 60));

        Task doneNew = task("t-done-new", userId, TaskStatus.DONE, 1);
        doneNew.setArchived(false);
        doneNew.setCompletedAt(Instant.now());

        Task todo = task("t-todo", userId, TaskStatus.TODO, 0);

        when(repo.findByOwnerUserId(userId)).thenReturn(new java.util.ArrayList<>(List.of(doneOld, doneNew, todo)));

        service.listForUser(userId);

        assertThat(doneOld.isArchived()).isTrue();
        assertThat(doneOld.getArchivedAt()).isNotNull();
        assertThat(doneNew.isArchived()).isFalse();
        verify(repo, atLeastOnce()).saveAll(any());
    }

    @Test
    void moveAcrossColumns_reindexesBothColumns() {
        TaskRepository repo = mock(TaskRepository.class);
        TaskService service = new TaskService(repo);

        String userId = "u1";

        Task t1 = task("t1", userId, TaskStatus.TODO, 0);
        Task t2 = task("t2", userId, TaskStatus.TODO, 1);
        Task t3 = task("t3", userId, TaskStatus.IN_PROGRESS, 0);

        when(repo.findById("t2")).thenReturn(Optional.of(t2));
        when(repo.findByOwnerUserIdAndStatusOrderByPositionAsc(userId, TaskStatus.TODO)).thenReturn(List.of(t1, t2));
        when(repo.findByOwnerUserIdAndStatusOrderByPositionAsc(userId, TaskStatus.IN_PROGRESS)).thenReturn(List.of(t3));
        when(repo.findByOwnerUserId(userId)).thenReturn(new java.util.ArrayList<>(List.of(t1, t2, t3)));

        service.moveForUser(userId, new MoveTaskRequest("t2", TaskStatus.TODO, TaskStatus.IN_PROGRESS, 0));

        assertThat(t1.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(t1.getPosition()).isEqualTo(0);

        assertThat(t2.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(t2.getPosition()).isEqualTo(0);

        assertThat(t3.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(t3.getPosition()).isEqualTo(1);

        verify(repo, atLeastOnce()).saveAll(any());
    }

    @Test
    void reorderWithinColumn_reindexesPositions() {
        TaskRepository repo = mock(TaskRepository.class);
        TaskService service = new TaskService(repo);

        String userId = "u1";

        Task t1 = task("t1", userId, TaskStatus.TODO, 0);
        Task t2 = task("t2", userId, TaskStatus.TODO, 1);
        Task t3 = task("t3", userId, TaskStatus.TODO, 2);

        when(repo.findById("t1")).thenReturn(Optional.of(t1));
        when(repo.findByOwnerUserIdAndStatusOrderByPositionAsc(userId, TaskStatus.TODO)).thenReturn(List.of(t1, t2, t3));
        when(repo.findByOwnerUserId(userId)).thenReturn(new java.util.ArrayList<>(List.of(t1, t2, t3)));

        service.moveForUser(userId, new MoveTaskRequest("t1", TaskStatus.TODO, TaskStatus.TODO, 2));

        assertThat(t1.getPosition()).isEqualTo(2);
        assertThat(t2.getPosition()).isEqualTo(0);
        assertThat(t3.getPosition()).isEqualTo(1);

        verify(repo, atLeastOnce()).saveAll(any());
    }

    @Test
    void search_matchesCommentMessages() {
        TaskRepository repo = mock(TaskRepository.class);
        TaskService service = new TaskService(repo);

        String userId = "u1";

        Task t = task("t1", userId, TaskStatus.TODO, 0);
        TaskComment c = new TaskComment();
        c.setId("c1");
        c.setAuthorUserId(userId);
        c.setAuthorEmail("u1@example.com");
        c.setMessage("Please review the PR");
        c.setCreatedAt(Instant.now());
        t.setComments(List.of(c));

        when(repo.findByOwnerUserId(userId)).thenReturn(new java.util.ArrayList<>(List.of(t)));

        List<com.glideclouds.taskmanagementsystem.tasks.dto.TaskResponse> res = service.searchForUser(userId, "review");
        assertThat(res).hasSize(1);
        assertThat(res.getFirst().id()).isEqualTo("t1");
    }

    @Test
    void updateDependencies_savesBlockedByTaskIds() {
        TaskRepository repo = mock(TaskRepository.class);
        TaskService service = new TaskService(repo);

        String userId = "u1";

        Task t = task("t-main", userId, TaskStatus.TODO, 0);
        Task dep1 = task("dep-1", userId, TaskStatus.DONE, 0);
        Task dep2 = task("dep-2", userId, TaskStatus.TODO, 0);

        when(repo.findById("t-main")).thenReturn(Optional.of(t));
        when(repo.findAllById(List.of("dep-1", "dep-2"))).thenReturn(List.of(dep1, dep2));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.updateDependenciesForUser(userId, "t-main", new UpdateDependenciesRequest(List.of("dep-1", "dep-2")));

        assertThat(t.getBlockedByTaskIds()).containsExactly("dep-1", "dep-2");
        verify(repo).save(eq(t));
    }

    @Test
    void updateDependencies_rejectsMissingDependencyTask() {
        TaskRepository repo = mock(TaskRepository.class);
        TaskService service = new TaskService(repo);

        String userId = "u1";
        Task t = task("t-main", userId, TaskStatus.TODO, 0);

        when(repo.findById("t-main")).thenReturn(Optional.of(t));
        when(repo.findAllById(List.of("dep-1"))).thenReturn(List.of());

        assertThatThrownBy(() -> service.updateDependenciesForUser(userId, "t-main", new UpdateDependenciesRequest(List.of("dep-1"))))
                .isInstanceOf(org.springframework.web.server.ResponseStatusException.class);
    }

    private static Task task(String id, String owner, TaskStatus status, int position) {
        Task t = new Task();
        t.setId(id);
        t.setOwnerUserId(owner);
        t.setStatus(status);
        t.setTitle("title" + id);
        t.setPriority(TaskPriority.MEDIUM);
        t.setPosition(position);
        return t;
    }
}
