package com.glideclouds.taskmanagementsystem.auth;

import com.glideclouds.taskmanagementsystem.auth.dto.AuthResponse;
import com.glideclouds.taskmanagementsystem.auth.dto.LoginRequest;
import com.glideclouds.taskmanagementsystem.auth.dto.MeResponse;
import com.glideclouds.taskmanagementsystem.auth.dto.RegisterRequest;
import com.glideclouds.taskmanagementsystem.security.CustomUserDetails;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
/** Authentication endpoints (register/login) and current user identity (me). */
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public void register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @GetMapping("/me")
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
