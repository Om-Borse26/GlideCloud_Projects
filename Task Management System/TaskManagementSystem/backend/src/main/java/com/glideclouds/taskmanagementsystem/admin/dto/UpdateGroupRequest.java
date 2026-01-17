package com.glideclouds.taskmanagementsystem.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdateGroupRequest(
        @Size(max = 500) List<@NotBlank String> memberEmails
) {
}
