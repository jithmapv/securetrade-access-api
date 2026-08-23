package com.securetrade.accessapi.service;

import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.UserRole;
import com.securetrade.accessapi.common.exception.DuplicateResourceException;
import com.securetrade.accessapi.common.exception.ResourceNotFoundException;
import com.securetrade.accessapi.dto.request.CreateAgentRequest;
import com.securetrade.accessapi.dto.response.AgentProfileResponse;
import com.securetrade.accessapi.entity.TradingAgentEntity;
import com.securetrade.accessapi.entity.UserEntity;
import com.securetrade.accessapi.repository.TradingAgentRepository;
import com.securetrade.accessapi.repository.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.SQLException;
import java.util.UUID;

@Service
public class TradingAgentService {

    private final TradingAgentRepository tradingAgentRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuditLogService auditLogService;

    public TradingAgentService(
            TradingAgentRepository tradingAgentRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuditLogService auditLogService) {

        this.tradingAgentRepository = tradingAgentRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public AgentProfileResponse registerAgent(CreateAgentRequest request) {
        // Check if username already exists
        if (userRepository.existsByUsername(request.getUsername())) {
            throw new DuplicateResourceException("Username already exists");
        }

        // Check if agent code already exists
        if (tradingAgentRepository.existsByAgentCode(request.getAgentCode())) {
            throw new DuplicateResourceException("Agent code already exists");
        }

        // Create user account for trading agent
        UserEntity user = new UserEntity(
                request.getUsername(),
                passwordEncoder.encode(request.getPassword()),
                UserRole.TRADING_AGENT,
                AgentStatus.ACTIVE);

        try {
            // Save now so database checks run here
            UserEntity savedUser = userRepository.saveAndFlush(user);

            TradingAgentEntity agent = new TradingAgentEntity(
                    savedUser,
                    request.getAgentCode(),
                    request.getName(),
                    request.getStrategyType(),
                    request.getMaxAllowedVolume());

            TradingAgentEntity savedAgent = tradingAgentRepository.saveAndFlush(agent);
            return toResponse(savedAgent);
        } catch (DataIntegrityViolationException exception) {
            if (isUniqueViolation(exception)) {
                // Stop duplicate data from concurrent requests
                throw new DuplicateResourceException(
                        "Username or agent code already exists",
                        exception);
            }

            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public AgentProfileResponse getAgentProfileByUsername(String username) {
        // Get user from database
        UserEntity user = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("Agent profile not found"));

        TradingAgentEntity agent = tradingAgentRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Agent profile not found"));

        return toResponse(agent);
    }

    @Transactional(readOnly = true)
    public AgentProfileResponse getAgentProfileById(UUID agentId) {
        TradingAgentEntity agent = findAgent(agentId);
        return toResponse(agent);
    }

    @Transactional
    public AgentProfileResponse updateAgentStatus(
            UUID agentId,
            AgentStatus newStatus,
            String adminUsername) {

        // Lock agent while admin updates status
        TradingAgentEntity agent = tradingAgentRepository.findByIdForUpdate(agentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Agent profile not found"));

        // Update agent account status
        UserEntity user = agent.getUser();
        AgentStatus previousStatus = user.getStatus();
        user.setStatus(newStatus);
        userRepository.save(user);

        // Save agent status audit
        auditLogService.logAction(
                null,
                adminUsername,
                AuditLogService.AGENT_STATUS_CHANGE,
                previousStatus.name(),
                newStatus.name(),
                "Agent ID: " + agentId);

        return toResponse(agent);
    }

    private TradingAgentEntity findAgent(UUID agentId) {
        // Get agent profile from database
        return tradingAgentRepository.findById(agentId)
                .orElseThrow(() -> new ResourceNotFoundException("Agent profile not found"));
    }

    private boolean isUniqueViolation(Throwable exception) {
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof SQLException sqlException
                    && "23505".equals(sqlException.getSQLState())) {
                return true;
            }

            cause = cause.getCause();
        }

        return false;
    }

    private AgentProfileResponse toResponse(TradingAgentEntity agent) {
        UserEntity user = agent.getUser();

        return new AgentProfileResponse(
                agent.getId(),
                user.getId(),
                user.getUsername(),
                agent.getAgentCode(),
                agent.getName(),
                agent.getStrategyType(),
                agent.getMaxAllowedVolume(),
                user.getStatus(),
                user.getRole(),
                agent.getCreatedAt());
    }
}
