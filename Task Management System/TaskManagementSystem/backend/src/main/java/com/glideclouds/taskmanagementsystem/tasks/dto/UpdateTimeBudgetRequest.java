package com.glideclouds.taskmanagementsystem.tasks.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record UpdateTimeBudgetRequest(
        @Min(0) @Max(100000) Integer timeBudgetMinutes
) {
}
