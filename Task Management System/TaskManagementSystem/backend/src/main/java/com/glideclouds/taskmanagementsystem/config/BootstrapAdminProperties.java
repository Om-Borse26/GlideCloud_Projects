package com.glideclouds.taskmanagementsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapAdminProperties(String adminEmail, String adminPassword) {
}
