package com.glideclouds.taskmanagementsystem.tasks;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RecurrenceRule {

    private RecurrenceFrequency frequency;

    /**
     * Repeat every N units (days/weeks/months). Defaults to 1.
     */
    private int interval = 1;

    /**
     * If true, skip weekends when calculating the next due date.
     */
    private boolean weekdaysOnly;

    /**
     * For WEEKLY recurrence: allowed ISO day-of-week values 1..7 (Mon..Sun).
     * When empty, defaults to "every interval weeks".
     */
    private List<Integer> daysOfWeek = new ArrayList<>();

    /**
     * Optional end date for recurrence.
     */
    private LocalDate endDate;

    /**
     * For MONTHLY recurrence: if set (>=1), schedule on the Nth business day of the month.
     * Example: 1 => first business day (Mon-Fri).
     */
    private Integer nthBusinessDayOfMonth;

    public RecurrenceRule() {
    }

    public RecurrenceFrequency getFrequency() {
        return frequency;
    }

    public void setFrequency(RecurrenceFrequency frequency) {
        this.frequency = frequency;
    }

    public int getInterval() {
        return interval;
    }

    public void setInterval(int interval) {
        this.interval = interval;
    }

    public boolean isWeekdaysOnly() {
        return weekdaysOnly;
    }

    public void setWeekdaysOnly(boolean weekdaysOnly) {
        this.weekdaysOnly = weekdaysOnly;
    }

    public List<Integer> getDaysOfWeek() {
        return daysOfWeek;
    }

    public void setDaysOfWeek(List<Integer> daysOfWeek) {
        this.daysOfWeek = daysOfWeek;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public void setEndDate(LocalDate endDate) {
        this.endDate = endDate;
    }

    public Integer getNthBusinessDayOfMonth() {
        return nthBusinessDayOfMonth;
    }

    public void setNthBusinessDayOfMonth(Integer nthBusinessDayOfMonth) {
        this.nthBusinessDayOfMonth = nthBusinessDayOfMonth;
    }
}
