package com.glideclouds.taskmanagementsystem.tasks;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaskDiscussionRepository extends MongoRepository<TaskDiscussion, String> {
}
