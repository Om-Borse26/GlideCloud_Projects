package com.glideclouds.taskmanagementsystem.config;

import com.glideclouds.taskmanagementsystem.users.Role;
import com.glideclouds.taskmanagementsystem.users.User;
import com.glideclouds.taskmanagementsystem.users.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class BootstrapAdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAdminInitializer.class);

    private final BootstrapAdminProperties props;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAdminInitializer(BootstrapAdminProperties props,
                                    UserRepository userRepository,
                                    PasswordEncoder passwordEncoder) {
        this.props = props;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        String email = normalize(props.adminEmail());
        String password = props.adminPassword();

        if (email == null || email.isBlank()) {
            return;
        }
        if (password == null || password.isBlank()) {
            log.warn("Bootstrap admin email is set but password is empty; skipping admin creation (email={})", email);
            return;
        }

        Optional<User> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.getRole() != Role.ADMIN) {
                user.setRole(Role.ADMIN);
                userRepository.save(user);
                log.info("Promoted existing user to ADMIN (email={})", email);
            } else {
                log.info("Bootstrap admin already exists (email={})", email);
            }
            return;
        }

        String hash = passwordEncoder.encode(password);
        User admin = new User(email, hash, Role.ADMIN);
        userRepository.save(admin);
        log.info("Created bootstrap ADMIN user (email={})", email);
    }

    private static String normalize(String email) {
        return email == null ? null : email.trim().toLowerCase();
    }
}
