package com.securetrade.accessapi.config;

import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializerConfig {

    private static final String ADMIN_USERNAME = "admin_user";
    private static final String ADMIN_PASSWORD = "AdminPassword123!";

    @Bean
    public CommandLineRunner initializeDefaultAdmin(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder) {

        return args -> {
            // Check if default admin exists
            if (userRepository.existsByUsername(ADMIN_USERNAME)) {
                return;
            }

            // Hash default admin password
            String passwordHash = passwordEncoder.encode(ADMIN_PASSWORD);
            UserEntity admin = new UserEntity(
                    ADMIN_USERNAME,
                    passwordHash,
                    UserRole.ADMIN,
                    AgentStatus.ACTIVE);

            // Save initial admin user
            userRepository.save(admin);
        };
    }
}
