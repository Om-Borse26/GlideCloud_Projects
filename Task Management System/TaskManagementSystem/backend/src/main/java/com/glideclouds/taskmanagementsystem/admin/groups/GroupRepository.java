package com.glideclouds.taskmanagementsystem.admin.groups;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GroupRepository extends MongoRepository<Group, String> {
    boolean existsByName(String name);
}
