package com.glideclouds.taskmanagementsystem.tasks.dto;

import jakarta.validation.constraints.Size;

public record TimerNoteRequest(
        @Size(max = 200) String note
) {
}
