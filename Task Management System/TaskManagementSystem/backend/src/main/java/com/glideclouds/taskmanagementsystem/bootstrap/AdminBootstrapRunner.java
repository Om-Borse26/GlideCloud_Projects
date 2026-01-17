package com.glideclouds.taskmanagementsystem.bootstrap;

import com.glideclouds.taskmanagementsystem.config.BootstrapAdminProperties;
import com.glideclouds.taskmanagementsystem.users.Role;
import com.glideclouds.taskmanagementsystem.users.User;
import com.glideclouds.taskmanagementsystem.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrapRunner.class);

    private final BootstrapAdminProperties props;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(BootstrapAdminProperties props,
                               UserRepository userRepository,
                               PasswordEncoder passwordEncoder) {
        this.props = props;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = props.adminEmail() == null ? "" : props.adminEmail().trim().toLowerCase();
        String password = props.adminPassword() == null ? "" : props.adminPassword().trim();

        if (email.isBlank() && password.isBlank()) {
            return;
        }

        if (email.isBlank() || password.isBlank()) {
            log.warn("Admin bootstrap skipped: both ADMIN_EMAIL and ADMIN_PASSWORD must be set.");
            return;
        }

        User existing = userRepository.findByEmail(email).orElse(null);
        if (existing != null) {
            if (existing.getRole() != Role.ADMIN) {
                existing.setRole(Role.ADMIN);
                userRepository.save(existing);
                log.info("Bootstrapped ADMIN role for existing user {}", email);
            } else {
                log.info("Admin bootstrap: user already exists: {}", email);
            }
            return;
        }

        String hash = passwordEncoder.encode(password);
        User admin = new User(email, hash, Role.ADMIN);
        userRepository.save(admin);
        log.info("Bootstrapped admin user: {}", email);
    }
}
