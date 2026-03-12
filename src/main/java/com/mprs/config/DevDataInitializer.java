package com.mprs.config;

import com.mprs.security.UserEntity;
import com.mprs.security.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Seeds the H2 in-memory database with default users for dev/testing.
 * Only active when spring.profiles.active=dev.
 *
 * In production, seed users come from V1__init_schema.sql via Flyway.
 */
@Slf4j
@Component
@Profile("dev")                   // ONLY runs when dev profile is active
@RequiredArgsConstructor
public class DevDataInitializer implements CommandLineRunner {

    private final UserRepository  userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        log.info("Dev profile — seeding default users into H2...");

        createUser("admin",   "admin123",   "ADMIN");
        createUser("analyst", "analyst123", "FINANCE_ANALYST");
        createUser("system",  "system123",  "SYSTEM");

        log.info("Dev seed complete — users: admin / analyst / system");
    }

    private void createUser(String username, String password, String role) {
        if (!userRepository.existsByUsername(username)) {
            UserEntity user = new UserEntity();
            user.setUsername(username);
            user.setPassword(passwordEncoder.encode(password));  // BCrypt hash
            user.setRole(role);
            userRepository.save(user);
            log.debug("Created dev user: {} with role: {}", username, role);
        }
    }
}