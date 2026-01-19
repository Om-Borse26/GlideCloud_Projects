package com.glideclouds.taskmanagementsystem;

import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

// @Testcontainers (Removed to use local docker-compose instance)
public abstract class AbstractMongoIntegrationTest {

    // Connect to the local MongoDB running via docker-compose (port 27017)
    // Use a specific test database 'tm_test_db' to avoid clashing with dev data
    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> "mongodb://localhost:27017/tm_test_db");
    }

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.data.mongodb.core.MongoTemplate mongoTemplate;

    @org.junit.jupiter.api.BeforeEach
    void cleanup() {
        // Clean the database before each test to ensure isolation
        mongoTemplate.getDb().drop();
    }
}
