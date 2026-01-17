package com.glideclouds.taskmanagementsystem.tasks.dto;

import com.glideclouds.taskmanagementsystem.tasks.RecurrenceFrequency;

import java.time.LocalDate;
import java.util.List;

public record RecurrenceRuleResponse(
        RecurrenceFrequency frequency,
        int interval,
        boolean weekdaysOnly,
        List<Integer> daysOfWeek,
        LocalDate endDate,
        Integer nthBusinessDayOfMonth
) {
}
