package com.securetrade.accessapi.dto.response;

import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.UserRole;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public class AgentProfileResponse {

    private final UUID id;
    private final UUID userId;
    private final String username;
    private final String agentCode;
    private final String name;
    private final String strategyType;
    private final BigDecimal maxAllowedVolume;
    private final AgentStatus status;
    private final UserRole role;
    private final Instant createdAt;

    public AgentProfileResponse(
            UUID id,
            UUID userId,
            String username,
            String agentCode,
            String name,
            String strategyType,
            BigDecimal maxAllowedVolume,
            AgentStatus status,
            UserRole role,
            Instant createdAt) {

        this.id = id;
        this.userId = userId;
        this.username = username;
        this.agentCode = agentCode;
        this.name = name;
        this.strategyType = strategyType;
        this.maxAllowedVolume = maxAllowedVolume;
        this.status = status;
        this.role = role;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public String getName() {
        return name;
    }

    public String getStrategyType() {
        return strategyType;
    }

    public BigDecimal getMaxAllowedVolume() {
        return maxAllowedVolume;
    }

    public AgentStatus getStatus() {
        return status;
    }

    public UserRole getRole() {
        return role;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
