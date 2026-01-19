package com.glideclouds.taskmanagementsystem.auth;

import com.glideclouds.taskmanagementsystem.auth.dto.AuthResponse;
import com.glideclouds.taskmanagementsystem.auth.dto.LoginRequest;
import com.glideclouds.taskmanagementsystem.auth.dto.MeResponse;
import com.glideclouds.taskmanagementsystem.auth.dto.RegisterRequest;
import com.glideclouds.taskmanagementsystem.security.CustomUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Authentication endpoints (register/login) and current user identity")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Register a new user", description = "Creates a new user account.")
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @PostMapping("/login")
    @Operation(summary = "Login", description = "Authenticates a user and returns a JWT token.")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
    @Operation(summary = "Get current user", description = "Returns the identity of the currently authenticated user.", security = @SecurityRequirement(name = "bearerAuth"))
    public MeResponse me(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }
        if (authentication.getPrincipal() instanceof CustomUserDetails cud) {
            String role = cud.getAuthorities().stream().findFirst()
                    .map(a -> a.getAuthority().replaceFirst("^ROLE_", ""))
                    .orElse("USER");
            return new MeResponse(cud.getId(), cud.getUsername(), role);
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
    }
}
