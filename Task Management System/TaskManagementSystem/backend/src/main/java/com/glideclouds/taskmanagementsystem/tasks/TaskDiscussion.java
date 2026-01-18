package com.glideclouds.taskmanagementsystem.tasks;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "task_discussions")
public class TaskDiscussion {

    @Id
    private String id;

    private List<TaskComment> comments = new ArrayList<>();

    private List<TaskDecision> decisions = new ArrayList<>();

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public TaskDiscussion() {
    }

    public TaskDiscussion(String id) {
        this.id = id;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public List<TaskComment> getComments() {
        return comments;
    }

    public void setComments(List<TaskComment> comments) {
        this.comments = comments;
    }

    public List<TaskDecision> getDecisions() {
        return decisions;
    }

    public void setDecisions(List<TaskDecision> decisions) {
        this.decisions = decisions;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
