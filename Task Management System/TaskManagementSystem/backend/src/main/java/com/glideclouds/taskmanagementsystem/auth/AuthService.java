package com.glideclouds.taskmanagementsystem.auth;

import com.glideclouds.taskmanagementsystem.auth.dto.AuthResponse;
import com.glideclouds.taskmanagementsystem.auth.dto.LoginRequest;
import com.glideclouds.taskmanagementsystem.auth.dto.RegisterRequest;
import com.glideclouds.taskmanagementsystem.security.JwtService;
import com.glideclouds.taskmanagementsystem.users.Role;
import com.glideclouds.taskmanagementsystem.users.User;
import com.glideclouds.taskmanagementsystem.users.UserRepository;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       AuthenticationManager authenticationManager,
                       JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
    }

    public void register(RegisterRequest request) {
        String email = request.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new ResponseStatusException(CONFLICT, "Email already registered");
        }

        String hash = passwordEncoder.encode(request.password());
        Role role = email.startsWith("admin") ? Role.ADMIN : Role.USER;
        User user = new User(email, hash, role);
        userRepository.save(user);
    }

    public AuthResponse login(LoginRequest request) {
        String email = request.email().trim().toLowerCase();
        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.password())
            );
        } catch (AuthenticationException e) {
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid email or password");
        }

        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new ResponseStatusException(UNAUTHORIZED, "Invalid email or password"));

        return new AuthResponse(jwtService.generateToken(user));
    }
}
