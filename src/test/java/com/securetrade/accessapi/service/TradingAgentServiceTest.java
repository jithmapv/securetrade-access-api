package com.securetrade.accessapi.service;

import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.common.exception.DuplicateResourceException;
import com.securetrade.accessapi.dto.request.CreateAgentRequest;
import com.securetrade.accessapi.dto.response.AgentProfileResponse;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import com.securetrade.accessapi.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TradingAgentServiceTest {

    private static final UUID USER_ID = UUID.fromString("7c4828aa-d7cd-45be-afbc-8cb06d7cc7cc");
    private static final UUID AGENT_ID = UUID.fromString("7c6b1c42-f3e5-4541-9be4-dce5f4cb39e2");
    private static final Instant CREATED_AT = Instant.parse("2026-08-23T06:30:00Z");

    @Mock
    private TradingAgentRepository tradingAgentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuditLogService auditLogService;

    @InjectMocks
    private TradingAgentService tradingAgentService;

    private CreateAgentRequest request;

    @BeforeEach
    void setUp() {
        request = new CreateAgentRequest(
                "agent.one",
                "raw-password",
                "AGT-001",
                "Agent One",
                "MOMENTUM",
                new BigDecimal("250000.00"));
    }

    @Test
    void registerAgentCreatesUserAndProfile() {
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(tradingAgentRepository.existsByAgentCode(request.getAgentCode())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenAnswer(invocation -> {
            UserEntity user = invocation.getArgument(0);
            user.setId(USER_ID);
            return user;
        });
        when(tradingAgentRepository.saveAndFlush(any(TradingAgentEntity.class))).thenAnswer(invocation -> {
            TradingAgentEntity agent = invocation.getArgument(0);
            agent.setId(AGENT_ID);
            agent.setCreatedAt(CREATED_AT);
            return agent;
        });

        AgentProfileResponse response = tradingAgentService.registerAgent(request);

        ArgumentCaptor<UserEntity> userCaptor = ArgumentCaptor.forClass(UserEntity.class);
        verify(userRepository).saveAndFlush(userCaptor.capture());
        UserEntity savedUser = userCaptor.getValue();

        assertThat(savedUser.getUsername()).isEqualTo("agent.one");
        assertThat(savedUser.getPasswordHash()).isEqualTo("encoded-password");
        assertThat(savedUser.getRole()).isEqualTo(UserRole.TRADING_AGENT);
        assertThat(savedUser.getStatus()).isEqualTo(AgentStatus.ACTIVE);

        ArgumentCaptor<TradingAgentEntity> agentCaptor =
                ArgumentCaptor.forClass(TradingAgentEntity.class);
        verify(tradingAgentRepository).saveAndFlush(agentCaptor.capture());
        TradingAgentEntity savedAgent = agentCaptor.getValue();

        assertThat(savedAgent.getUser()).isSameAs(savedUser);
        assertThat(savedAgent.getAgentCode()).isEqualTo("AGT-001");
        assertThat(savedAgent.getName()).isEqualTo("Agent One");
        assertThat(savedAgent.getStrategyType()).isEqualTo("MOMENTUM");
        assertThat(savedAgent.getMaxAllowedVolume())
                .isEqualByComparingTo("250000.00");

        assertThat(response.getId()).isEqualTo(AGENT_ID);
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        assertThat(response.getUsername()).isEqualTo("agent.one");
        assertThat(response.getRole()).isEqualTo(UserRole.TRADING_AGENT);
        assertThat(response.getStatus()).isEqualTo(AgentStatus.ACTIVE);
        assertThat(response.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void duplicateUsernameDoesNotSaveAgent() {
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(true);

        assertThatThrownBy(() -> tradingAgentService.registerAgent(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        verify(tradingAgentRepository, never()).saveAndFlush(any(TradingAgentEntity.class));
    }

    @Test
    void duplicateAgentCodeDoesNotSaveAgent() {
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(tradingAgentRepository.existsByAgentCode(request.getAgentCode())).thenReturn(true);

        assertThatThrownBy(() -> tradingAgentService.registerAgent(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).saveAndFlush(any(UserEntity.class));
        verify(tradingAgentRepository, never()).saveAndFlush(any(TradingAgentEntity.class));
    }

    @Test
    void databaseDuplicateBecomesDuplicateResourceError() {
        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(tradingAgentRepository.existsByAgentCode(request.getAgentCode())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(UserEntity.class)))
                .thenThrow(new DataIntegrityViolationException(
                        "duplicate",
                        new SQLException("duplicate", "23505")));

        assertThatThrownBy(() -> tradingAgentService.registerAgent(request))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessage("Username or agent code already exists")
                .hasCauseInstanceOf(DataIntegrityViolationException.class);

        verify(tradingAgentRepository, never()).saveAndFlush(any(TradingAgentEntity.class));
    }

    @Test
    void otherDatabaseErrorsAreNotChangedToDuplicateErrors() {
        DataIntegrityViolationException databaseError = new DataIntegrityViolationException(
                "value too long",
                new SQLException("value too long", "22001"));

        when(userRepository.existsByUsername(request.getUsername())).thenReturn(false);
        when(tradingAgentRepository.existsByAgentCode(request.getAgentCode())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(UserEntity.class))).thenThrow(databaseError);

        assertThatThrownBy(() -> tradingAgentService.registerAgent(request))
                .isSameAs(databaseError);

        verify(tradingAgentRepository, never()).saveAndFlush(any(TradingAgentEntity.class));
    }

    @Test
    void getAgentProfileByUsernameReturnsProfile() {
        UserEntity user = createUser();
        TradingAgentEntity agent = createAgent(user);

        when(userRepository.findByUsername(user.getUsername())).thenReturn(Optional.of(user));
        when(tradingAgentRepository.findByUserId(USER_ID)).thenReturn(Optional.of(agent));

        AgentProfileResponse response =
                tradingAgentService.getAgentProfileByUsername(user.getUsername());

        assertThat(response.getId()).isEqualTo(AGENT_ID);
        assertThat(response.getUserId()).isEqualTo(USER_ID);
        assertThat(response.getUsername()).isEqualTo("agent.one");
        assertThat(response.getAgentCode()).isEqualTo("AGT-001");
        assertThat(response.getStatus()).isEqualTo(AgentStatus.ACTIVE);
        verify(tradingAgentRepository).findByUserId(USER_ID);
    }

    @Test
    void updateAgentStatusChangesLinkedUser() {
        UserEntity user = createUser();
        TradingAgentEntity agent = createAgent(user);
        when(tradingAgentRepository.findByIdForUpdate(AGENT_ID))
                .thenReturn(Optional.of(agent));
        when(userRepository.save(user)).thenReturn(user);

        AgentProfileResponse response =
                tradingAgentService.updateAgentStatus(
                        AGENT_ID,
                        AgentStatus.SUSPENDED,
                        "admin.one");

        assertThat(user.getStatus()).isEqualTo(AgentStatus.SUSPENDED);
        assertThat(response.getStatus()).isEqualTo(AgentStatus.SUSPENDED);
        assertThat(response.getId()).isEqualTo(AGENT_ID);
        verify(userRepository).save(user);
        verify(tradingAgentRepository, never()).save(any(TradingAgentEntity.class));
        verify(auditLogService).logAction(
                null,
                "admin.one",
                AuditLogService.AGENT_STATUS_CHANGE,
                AgentStatus.ACTIVE.name(),
                AgentStatus.SUSPENDED.name(),
                "Agent ID: " + AGENT_ID);
    }

    private UserEntity createUser() {
        UserEntity user = new UserEntity(
                "agent.one",
                "encoded-password",
                UserRole.TRADING_AGENT,
                AgentStatus.ACTIVE);
        user.setId(USER_ID);
        return user;
    }

    private TradingAgentEntity createAgent(UserEntity user) {
        TradingAgentEntity agent = new TradingAgentEntity(
                user,
                "AGT-001",
                "Agent One",
                "MOMENTUM",
                new BigDecimal("250000.00"));
        agent.setId(AGENT_ID);
        agent.setCreatedAt(CREATED_AT);
        return agent;
    }
}
