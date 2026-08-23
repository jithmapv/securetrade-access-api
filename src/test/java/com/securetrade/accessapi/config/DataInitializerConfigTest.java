package com.securetrade.accessapi.config;

import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataInitializerConfigTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsDefaultAdminWhenMissing() throws Exception {
        when(userRepository.existsByUsername("admin_user")).thenReturn(false);
        when(passwordEncoder.encode("AdminPassword123!"))
                .thenReturn("encoded-password");

        DataInitializerConfig config = new DataInitializerConfig();
        CommandLineRunner runner = config.initializeDefaultAdmin(
                userRepository,
                passwordEncoder);

        runner.run();

        ArgumentCaptor<UserEntity> userCaptor =
                ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).save(userCaptor.capture());

        UserEntity savedUser = userCaptor.getValue();
        assertThat(savedUser.getUsername()).isEqualTo("admin_user");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.ADMIN);
        assertThat(savedUser.getStatus()).isEqualTo(AgentStatus.ACTIVE);
    }

    @Test
    void keepsExistingDefaultAdmin() throws Exception {
        when(userRepository.existsByUsername("admin_user")).thenReturn(true);

        DataInitializerConfig config = new DataInitializerConfig();
        CommandLineRunner runner = config.initializeDefaultAdmin(
                userRepository,
                passwordEncoder);

        runner.run();

        verify(userRepository, never()).save(org.mockito.ArgumentMatchers.any());
        verifyNoInteractions(passwordEncoder);
    }
}
