package com.glideclouds.taskmanagementsystem.ai;

import com.glideclouds.taskmanagementsystem.ai.dto.AiStatusResponse;
import com.glideclouds.taskmanagementsystem.ai.dto.AiTemplateRequest;
import com.glideclouds.taskmanagementsystem.ai.dto.AiTemplateResponse;
import com.glideclouds.taskmanagementsystem.security.SecurityUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiService aiService;

    public AiController(AiService aiService) {
        this.aiService = aiService;
    }

    @GetMapping("/status")
    public AiStatusResponse status() {
        return aiService.status();
    }

    @PostMapping("/task-assist")
    public AiTemplateResponse taskAssist(@Valid @RequestBody AiTemplateRequest request) {
        String userId = SecurityUtils.currentUserId();
        if (userId == null) {
            // Should be blocked by SecurityConfig, but keep it explicit.
            throw new ResponseStatusException(UNAUTHORIZED, "Unauthorized");
        }
        boolean isAdmin = SecurityUtils.currentHasRole("ADMIN");
        return aiService.taskAssistTemplate(userId, isAdmin, request.taskId(), request.prompt());
    }
}
