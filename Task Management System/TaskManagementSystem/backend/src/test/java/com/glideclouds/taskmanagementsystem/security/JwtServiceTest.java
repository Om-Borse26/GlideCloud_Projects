package com.glideclouds.taskmanagementsystem.security;

import com.glideclouds.taskmanagementsystem.config.JwtProperties;
import com.glideclouds.taskmanagementsystem.users.Role;
import com.glideclouds.taskmanagementsystem.users.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void generatesAndValidatesToken() {
        JwtProperties props = new JwtProperties("0123456789abcdef0123456789abcdef0123456789abcdef", 60_000);
        JwtService jwtService = new JwtService(props);

        User user = new User("user@example.com", "hash", Role.USER);
        user.setId("user-123");

        String token = jwtService.generateToken(user);
        assertThat(token).isNotBlank();
        assertThat(jwtService.isTokenValid(token)).isTrue();
        assertThat(jwtService.extractUserId(token)).isEqualTo("user-123");
    }
}
