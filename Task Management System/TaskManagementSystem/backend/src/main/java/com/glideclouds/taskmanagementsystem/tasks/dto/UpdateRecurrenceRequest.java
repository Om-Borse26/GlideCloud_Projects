package com.glideclouds.taskmanagementsystem.tasks.dto;

import com.glideclouds.taskmanagementsystem.tasks.RecurrenceFrequency;

import java.time.LocalDate;
import java.util.List;

/**
 * Set recurrence to null by sending frequency = null.
 */
public record UpdateRecurrenceRequest(
        RecurrenceFrequency frequency,
        Integer interval,
        Boolean weekdaysOnly,
        List<Integer> daysOfWeek,
        LocalDate endDate,
        Integer nthBusinessDayOfMonth
) {
}
