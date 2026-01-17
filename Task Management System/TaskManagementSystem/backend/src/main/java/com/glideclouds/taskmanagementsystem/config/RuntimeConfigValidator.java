package com.glideclouds.taskmanagementsystem.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;

@Component
/** Fail-fast production config validation (runs once on startup in the prod profile). */
class RuntimeConfigValidator implements ApplicationRunner {

    private final Environment environment;
    private final CorsProperties corsProperties;

    RuntimeConfigValidator(Environment environment, CorsProperties corsProperties) {
        this.environment = environment;
        this.corsProperties = corsProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!environment.acceptsProfiles(Profiles.of("prod"))) {
            return;
        }

        List<String> origins = corsProperties.allowedOrigins();
        boolean hasAnyOrigin = origins != null && origins.stream().anyMatch(StringUtils::hasText);

        if (!hasAnyOrigin) {
            throw new IllegalStateException(
                    "CORS is not configured for production. Set CORS_ALLOWED_ORIGINS (comma-separated) to the client origin(s)."
            );
        }
    }
}
