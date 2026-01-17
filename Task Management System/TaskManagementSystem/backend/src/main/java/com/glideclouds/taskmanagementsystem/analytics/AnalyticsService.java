package com.glideclouds.taskmanagementsystem.analytics;

import com.glideclouds.taskmanagementsystem.analytics.dto.AnalyticsOverviewResponse;
import com.glideclouds.taskmanagementsystem.analytics.dto.AnalyticsTrendPoint;
import com.glideclouds.taskmanagementsystem.analytics.dto.LabelCount;
import com.glideclouds.taskmanagementsystem.analytics.dto.StatusBottleneck;
import com.glideclouds.taskmanagementsystem.analytics.dto.TaskQuickView;
import com.glideclouds.taskmanagementsystem.tasks.Task;
import com.glideclouds.taskmanagementsystem.tasks.TaskRepository;
import com.glideclouds.taskmanagementsystem.tasks.TaskStatus;
import com.glideclouds.taskmanagementsystem.tasks.TaskTimeLog;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final TaskRepository taskRepository;

    public AnalyticsService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public AnalyticsOverviewResponse overviewForUser(String userId, Integer daysParam) {
        int days = daysParam == null ? 14 : Math.max(7, Math.min(60, daysParam));

        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = LocalDate.now(zone);
        LocalDate tomorrow = today.plusDays(1);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate trendStart = today.minusDays(days - 1L);
        LocalDate last30Start = today.minusDays(29);

        List<Task> tasks = taskRepository.findByOwnerUserId(userId);

        long totalTasks = tasks.size();
        long openTasks = 0;
        long focusOpenTasks = 0;
        long overdueOpenTasks = 0;
        long dueTodayOpenTasks = 0;
        long dueTomorrowOpenTasks = 0;
        long upcoming7DaysOpenTasks = 0;
        long completedToday = 0;
        long completedThisWeek = 0;
        long completedLast30Days = 0;
        long loggedMinutesToday = 0;
        long loggedMinutesThisWeek = 0;
        long loggedMinutesLast30Days = 0;

        Map<String, Long> statusCounts = new HashMap<>();
        Map<String, Long> labelCounts = new HashMap<>();
        Map<LocalDate, Long> completedByDay = new HashMap<>();
        Map<LocalDate, Long> completedByDayForStreak = new HashMap<>();
        Map<LocalDate, Long> loggedByDay = new HashMap<>();

        Map<String, List<Long>> openAgeDaysByStatus = new HashMap<>();

        List<Long> cycleTimesMinutesLast30 = new ArrayList<>();

        List<TaskQuickView> overdueTop = new ArrayList<>();
        List<TaskQuickView> dueTodayTop = new ArrayList<>();

        for (Task t : tasks) {
            TaskStatus status = t.getStatus();
            boolean done = status == TaskStatus.DONE;

            statusCounts.merge(status == null ? "UNKNOWN" : status.name(), 1L, Long::sum);

            if (!done) {
                openTasks++;
                if (t.isFocus()) {
                    focusOpenTasks++;
                }

                LocalDate due = t.getDueDate();
                if (due != null) {
                    if (due.isBefore(today)) overdueOpenTasks++;
                    if (due.isEqual(today)) dueTodayOpenTasks++;
                    if (due.isEqual(tomorrow)) dueTomorrowOpenTasks++;
                    if (!due.isBefore(today) && !due.isAfter(today.plusDays(7))) upcoming7DaysOpenTasks++;

                    if (due.isBefore(today)) {
                        overdueTop.add(toQuickView(t));
                    }
                    if (due.isEqual(today)) {
                        dueTodayTop.add(toQuickView(t));
                    }
                }

                for (String raw : t.getLabels() == null ? List.<String>of() : t.getLabels()) {
                    if (raw == null) continue;
                    String normalized = raw.trim().toLowerCase();
                    if (normalized.isBlank()) continue;
                    labelCounts.merge(normalized, 1L, Long::sum);
                }

                Instant createdAt = t.getCreatedAt();
                if (createdAt != null) {
                    long ageDays = Math.max(0, Duration.between(createdAt, Instant.now()).toDays());
                    String key = status == null ? "UNKNOWN" : status.name();
                    openAgeDaysByStatus.computeIfAbsent(key, k -> new ArrayList<>()).add(ageDays);
                }
            }

            Instant completedAt = t.getCompletedAt();
            if (completedAt != null) {
                LocalDate completedDate = LocalDate.ofInstant(completedAt, zone);
                if (completedDate.isEqual(today)) completedToday++;
                if (!completedDate.isBefore(weekStart) && !completedDate.isAfter(today)) completedThisWeek++;
                if (!completedDate.isBefore(last30Start) && !completedDate.isAfter(today)) {
                    completedLast30Days++;
                }
                if (!completedDate.isBefore(trendStart) && !completedDate.isAfter(today)) {
                    completedByDay.merge(completedDate, 1L, Long::sum);
                }

                // Streak: track a wider range (up to 60 days) to support long streaks.
                if (!completedDate.isBefore(today.minusDays(60)) && !completedDate.isAfter(today)) {
                    completedByDayForStreak.merge(completedDate, 1L, Long::sum);
                }

                Instant createdAt = t.getCreatedAt();
                if (createdAt != null && !completedAt.isBefore(createdAt)) {
                    if (!completedDate.isBefore(last30Start) && !completedDate.isAfter(today)) {
                        cycleTimesMinutesLast30.add(Duration.between(createdAt, completedAt).toMinutes());
                    }
                }
            }

            for (TaskTimeLog log : t.getTimeLogs() == null ? List.<TaskTimeLog>of() : t.getTimeLogs()) {
                if (log == null || log.getEndedAt() == null) continue;
                long minutes = log.getDurationMinutes();
                if (minutes <= 0) continue;

                LocalDate endedDate = LocalDate.ofInstant(log.getEndedAt(), zone);

                if (endedDate.isEqual(today)) loggedMinutesToday += minutes;
                if (!endedDate.isBefore(weekStart) && !endedDate.isAfter(today)) loggedMinutesThisWeek += minutes;
                if (!endedDate.isBefore(last30Start) && !endedDate.isAfter(today)) loggedMinutesLast30Days += minutes;
                if (!endedDate.isBefore(trendStart) && !endedDate.isAfter(today)) {
                    loggedByDay.merge(endedDate, minutes, Long::sum);
                }
            }
        }

        Double avgCycleTimeHoursLast30Days = null;
        if (!cycleTimesMinutesLast30.isEmpty()) {
            double avgMinutes = cycleTimesMinutesLast30.stream().mapToLong(x -> x).average().orElse(0);
            avgCycleTimeHoursLast30Days = avgMinutes / 60.0;
        }

        List<LabelCount> topOpenLabels = labelCounts.entrySet()
                .stream()
                .sorted((a, b) -> {
                    int cmp = Long.compare(b.getValue(), a.getValue());
                    if (cmp != 0) return cmp;
                    return a.getKey().compareTo(b.getKey());
                })
                .limit(8)
                .map(e -> new LabelCount(e.getKey(), e.getValue()))
                .toList();

            overdueTop.sort(Comparator
                .comparing(TaskQuickView::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparingInt(v -> priorityRank(v.priority()))
                .thenComparing(TaskQuickView::title, Comparator.nullsLast(Comparator.naturalOrder())));
            if (overdueTop.size() > 5) {
                overdueTop = overdueTop.subList(0, 5);
            }

            dueTodayTop.sort(Comparator
                .comparingInt((TaskQuickView v) -> priorityRank(v.priority()))
                .thenComparing(TaskQuickView::title, Comparator.nullsLast(Comparator.naturalOrder())));
            if (dueTodayTop.size() > 5) {
                dueTodayTop = dueTodayTop.subList(0, 5);
            }

        List<AnalyticsTrendPoint> trend = new ArrayList<>(days);
        for (int i = 0; i < days; i++) {
            LocalDate d = trendStart.plusDays(i);
            trend.add(new AnalyticsTrendPoint(
                    d,
                    completedByDay.getOrDefault(d, 0L),
                    loggedByDay.getOrDefault(d, 0L)
            ));
        }

        // Ensure stable ordering even if callers change the 'days' bounds in the future.
        trend.sort(Comparator.comparing(AnalyticsTrendPoint::date));

        long completionStreakDays = 0;
        for (LocalDate d = today; !d.isBefore(today.minusDays(60)); d = d.minusDays(1)) {
            if (completedByDayForStreak.getOrDefault(d, 0L) <= 0) break;
            completionStreakDays++;
        }

        List<StatusBottleneck> bottlenecks = openAgeDaysByStatus.entrySet().stream()
                .filter(e -> e.getKey() != null && !e.getKey().equalsIgnoreCase("DONE"))
                .map(e -> {
                    List<Long> ages = e.getValue() == null ? List.<Long>of() : e.getValue();
                    long count = ages.size();
                    long avg = count == 0 ? 0 : Math.round(ages.stream().mapToLong(x -> x).average().orElse(0));
                    long oldest = ages.stream().mapToLong(x -> x).max().orElse(0);
                    return new StatusBottleneck(e.getKey(), count, avg, oldest);
                })
                .sorted((a, b) -> {
                    int cmp = Long.compare(b.avgAgeDays(), a.avgAgeDays());
                    if (cmp != 0) return cmp;
                    cmp = Long.compare(b.openCount(), a.openCount());
                    if (cmp != 0) return cmp;
                    return String.valueOf(a.status()).compareTo(String.valueOf(b.status()));
                })
                .limit(5)
                .collect(Collectors.toList());

        return new AnalyticsOverviewResponse(
                Instant.now(),
                today,
                totalTasks,
                openTasks,
                focusOpenTasks,
                statusCounts,
                overdueOpenTasks,
                dueTodayOpenTasks,
                dueTomorrowOpenTasks,
                upcoming7DaysOpenTasks,
                completedToday,
                completedThisWeek,
                completedLast30Days,
            completionStreakDays,
                avgCycleTimeHoursLast30Days,
                loggedMinutesToday,
                loggedMinutesThisWeek,
                loggedMinutesLast30Days,
                topOpenLabels,
                overdueTop,
                dueTodayTop,
            trend,
            bottlenecks
        );
    }

    private static TaskQuickView toQuickView(Task t) {
        return new TaskQuickView(
                t.getId(),
                t.getTitle(),
                t.getStatus() == null ? null : t.getStatus().name(),
                t.getPriority() == null ? null : t.getPriority().name(),
                t.getDueDate(),
                t.getLabels() == null ? List.of() : t.getLabels(),
                t.isFocus()
        );
    }

    private static int priorityRank(String priority) {
        if (priority == null) return 99;
        return switch (priority) {
            case "HIGH" -> 0;
            case "MEDIUM" -> 1;
            case "LOW" -> 2;
            default -> 50;
        };
    }
}
