package com.glideclouds.taskmanagementsystem.analytics.dto;

public record StatusBottleneck(
        String status,
        long openCount,
        long avgAgeDays,
        long oldestAgeDays
) {
}
