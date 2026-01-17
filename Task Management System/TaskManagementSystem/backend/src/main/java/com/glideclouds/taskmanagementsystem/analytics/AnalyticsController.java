package com.glideclouds.taskmanagementsystem.analytics;

import com.glideclouds.taskmanagementsystem.analytics.dto.AnalyticsOverviewResponse;
import com.glideclouds.taskmanagementsystem.security.SecurityUtils;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @GetMapping("/overview")
    public AnalyticsOverviewResponse overview(@RequestParam(name = "days", required = false) Integer days) {
        String userId = requireUserId();
        return analyticsService.overviewForUser(userId, days);
    }

    private String requireUserId() {
        String userId = SecurityUtils.currentUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        return userId;
    }
}
