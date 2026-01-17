package com.glideclouds.taskmanagementsystem.analytics.dto;

import java.time.LocalDate;
import java.util.List;

public record TaskQuickView(
        String id,
        String title,
        String status,
        String priority,
        LocalDate dueDate,
        List<String> labels,
        boolean focus
) {
}
