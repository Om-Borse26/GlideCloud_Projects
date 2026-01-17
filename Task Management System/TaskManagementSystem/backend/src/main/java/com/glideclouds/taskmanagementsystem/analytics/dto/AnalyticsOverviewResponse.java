package com.glideclouds.taskmanagementsystem.analytics.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public record AnalyticsOverviewResponse(
        Instant generatedAt,
        LocalDate today,

        long totalTasks,
        long openTasks,
        long focusOpenTasks,
        Map<String, Long> statusCounts,

        long overdueOpenTasks,
        long dueTodayOpenTasks,
        long dueTomorrowOpenTasks,
        long upcoming7DaysOpenTasks,

        long completedToday,
        long completedThisWeek,
        long completedLast30Days,
        long completionStreakDays,
        Double avgCycleTimeHoursLast30Days,

        long loggedMinutesToday,
        long loggedMinutesThisWeek,
        long loggedMinutesLast30Days,

        List<LabelCount> topOpenLabels,
        List<TaskQuickView> overdueTop,
        List<TaskQuickView> dueTodayTop,
        List<AnalyticsTrendPoint> trend,

        List<StatusBottleneck> bottlenecks
) {
}
