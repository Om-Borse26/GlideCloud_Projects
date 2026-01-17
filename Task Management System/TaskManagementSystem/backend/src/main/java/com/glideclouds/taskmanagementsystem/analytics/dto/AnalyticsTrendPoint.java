package com.glideclouds.taskmanagementsystem.analytics.dto;

import java.time.LocalDate;

public record AnalyticsTrendPoint(
        LocalDate date,
        long completedCount,
        long loggedMinutes
) {
}
