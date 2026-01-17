package com.glideclouds.taskmanagementsystem.admin.groups;

import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "groups")
public class Group {

    @Id
    private String id;

    @Indexed(unique = true)
    private String name;

    private List<String> memberUserIds = new ArrayList<>();

    private String createdByAdminUserId;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;

    public Group() {
    }

    public Group(String name, List<String> memberUserIds, String createdByAdminUserId) {
        this.name = name;
        this.memberUserIds = memberUserIds != null ? new ArrayList<>(memberUserIds) : new ArrayList<>();
        this.createdByAdminUserId = createdByAdminUserId;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getMemberUserIds() {
        return memberUserIds;
    }

    public void setMemberUserIds(List<String> memberUserIds) {
        this.memberUserIds = memberUserIds != null ? new ArrayList<>(memberUserIds) : new ArrayList<>();
    }

    public String getCreatedByAdminUserId() {
        return createdByAdminUserId;
    }

    public void setCreatedByAdminUserId(String createdByAdminUserId) {
        this.createdByAdminUserId = createdByAdminUserId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
