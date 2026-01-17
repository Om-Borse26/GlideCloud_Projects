package com.glideclouds.taskmanagementsystem.tasks;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;

final class RecurrenceCalculator {

    private RecurrenceCalculator() {
    }

    static LocalDate nextDueDate(LocalDate base, RecurrenceRule rule) {
        if (base == null || rule == null || rule.getFrequency() == null) {
            return null;
        }

        int interval = Math.max(1, rule.getInterval());
        LocalDate candidate;

        switch (rule.getFrequency()) {
            case DAILY -> candidate = base.plusDays(interval);
            case WEEKLY -> candidate = nextWeekly(base, interval, rule.getDaysOfWeek());
            case MONTHLY -> candidate = nextMonthly(base, interval, rule.getNthBusinessDayOfMonth());
            default -> candidate = base.plusDays(interval);
        }

        if (rule.isWeekdaysOnly()) {
            candidate = bumpToWeekday(candidate);
        }

        if (rule.getEndDate() != null && candidate.isAfter(rule.getEndDate())) {
            return null;
        }

        return candidate;
    }

    private static LocalDate nextWeekly(LocalDate base, int interval, List<Integer> daysOfWeek) {
        if (daysOfWeek == null || daysOfWeek.isEmpty()) {
            return base.plusWeeks(interval);
        }

        List<Integer> days = daysOfWeek.stream()
                .filter(d -> d != null && d >= 1 && d <= 7)
                .distinct()
                .sorted(Comparator.naturalOrder())
                .toList();

        if (days.isEmpty()) {
            return base.plusWeeks(interval);
        }

        // Find the next allowed day after base, searching up to interval weeks ahead.
        int maxDaysToSearch = (interval * 7) + 7;
        for (int i = 1; i <= maxDaysToSearch; i++) {
            LocalDate d = base.plusDays(i);
            if (days.contains(d.getDayOfWeek().getValue())) {
                return d;
            }
        }

        return base.plusWeeks(interval);
    }

    private static LocalDate nextMonthly(LocalDate base, int interval) {
        return nextMonthly(base, interval, null);
    }

    private static LocalDate nextMonthly(LocalDate base, int interval, Integer nthBusinessDayOfMonth) {
        YearMonth target = YearMonth.from(base).plusMonths(interval);

        Integer nth = nthBusinessDayOfMonth;
        if (nth != null && nth > 0) {
            return nthBusinessDay(target, nth);
        }

        int day = Math.min(base.getDayOfMonth(), target.lengthOfMonth());
        return target.atDay(day);
    }

    private static LocalDate nthBusinessDay(YearMonth month, int nth) {
        int count = 0;
        LocalDate lastBusiness = null;
        for (int day = 1; day <= month.lengthOfMonth(); day++) {
            LocalDate d = month.atDay(day);
            DayOfWeek dow = d.getDayOfWeek();
            boolean business = dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY;
            if (!business) continue;
            lastBusiness = d;
            count++;
            if (count == nth) {
                return d;
            }
        }

        // Fallback: last business day if nth exceeds available business days.
        return lastBusiness != null ? lastBusiness : month.atDay(1);
    }

    private static LocalDate bumpToWeekday(LocalDate d) {
        DayOfWeek dow = d.getDayOfWeek();
        if (dow == DayOfWeek.SATURDAY) return d.plusDays(2);
        if (dow == DayOfWeek.SUNDAY) return d.plusDays(1);
        return d;
    }
}
