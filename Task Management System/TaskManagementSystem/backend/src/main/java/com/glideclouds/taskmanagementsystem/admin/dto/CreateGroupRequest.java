package com.glideclouds.taskmanagementsystem.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record CreateGroupRequest(
        @NotBlank @Size(max = 80) String name,
        @Size(max = 500) List<@NotBlank String> memberEmails
) {
}
