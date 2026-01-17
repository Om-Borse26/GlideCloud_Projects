package com.glideclouds.taskmanagementsystem.tasks;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RecurrenceCalculatorTest {

    @Test
    void monthlyNthBusinessDay_schedulesToNthBusinessDay() {
        // Base date in Jan; next month is Feb 2026.
        LocalDate base = LocalDate.of(2026, 1, 16);

        RecurrenceRule rule = new RecurrenceRule();
        rule.setFrequency(RecurrenceFrequency.MONTHLY);
        rule.setInterval(1);
        rule.setNthBusinessDayOfMonth(1);

        LocalDate next = RecurrenceCalculator.nextDueDate(base, rule);

        // Feb 2026: 1st business day is Monday Feb 2 (Feb 1 is Sunday)
        assertThat(next).isEqualTo(LocalDate.of(2026, 2, 2));
    }

    @Test
    void monthlyNthBusinessDay_fallsBackToLastBusinessDayWhenNthTooLarge() {
        LocalDate base = LocalDate.of(2026, 1, 16);

        RecurrenceRule rule = new RecurrenceRule();
        rule.setFrequency(RecurrenceFrequency.MONTHLY);
        rule.setInterval(1);
        rule.setNthBusinessDayOfMonth(99);

        LocalDate next = RecurrenceCalculator.nextDueDate(base, rule);

        YearMonth feb = YearMonth.of(2026, 2);
        // Last day is Feb 28, 2026 (Saturday), so last business day is Feb 27 (Friday)
        assertThat(next).isEqualTo(feb.atDay(27));
    }

    @Test
    void weeklyDaysOfWeek_picksNextAllowedDay() {
        // Fri Jan 16, 2026
        LocalDate base = LocalDate.of(2026, 1, 16);

        RecurrenceRule rule = new RecurrenceRule();
        rule.setFrequency(RecurrenceFrequency.WEEKLY);
        rule.setInterval(1);
        rule.setDaysOfWeek(List.of(1, 3)); // Mon, Wed

        LocalDate next = RecurrenceCalculator.nextDueDate(base, rule);
        // Next allowed day after Fri is Mon Jan 19, 2026
        assertThat(next).isEqualTo(LocalDate.of(2026, 1, 19));
    }
}
