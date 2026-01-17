package com.glideclouds.taskmanagementsystem.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record AppMailProperties(
        boolean enabled,
        String from
) {
}
