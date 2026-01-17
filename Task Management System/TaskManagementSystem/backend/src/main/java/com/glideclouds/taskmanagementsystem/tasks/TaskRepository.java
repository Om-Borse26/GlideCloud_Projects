package com.glideclouds.taskmanagementsystem.tasks;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.LocalDate;
import java.util.List;

public interface TaskRepository extends MongoRepository<Task, String> {
    List<Task> findByOwnerUserId(String ownerUserId);
    List<Task> findByOwnerUserIdAndStatusOrderByPositionAsc(String ownerUserId, TaskStatus status);

    List<Task> findByDueDateAndStatusNot(LocalDate dueDate, TaskStatus status);

    List<Task> findByDueDateBeforeAndStatusNot(LocalDate dueDate, TaskStatus status);
}
