package com.securetrade.accessapi.dto.response;

import com.securetrade.accessapi.common.enums.AgentStatus;
import com.securetrade.accessapi.common.enums.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Trading agent profile")
public class AgentProfileResponse {

    @Schema(description = "Agent profile ID", example = "550e8400-e29b-41d4-a716-446655440000")
    private final UUID id;

    @Schema(description = "Linked user ID", example = "40b383c1-302f-4bdb-818e-88afdbd61545")
    private final UUID userId;

    @Schema(description = "Login username", example = "agent.one")
    private final String username;

    @Schema(description = "Unique agent code", example = "AGENT-001")
    private final String agentCode;

    @Schema(description = "Agent display name", example = "Momentum Agent")
    private final String name;

    @Schema(description = "Trading strategy name", example = "MOMENTUM")
    private final String strategyType;

    @Schema(description = "Largest volume the agent may request", example = "2500000.00")
    private final BigDecimal maxAllowedVolume;

    @Schema(
            description = "Agent account status",
            example = "ACTIVE",
            allowableValues = {"ACTIVE", "INACTIVE", "SUSPENDED"})
    private final AgentStatus status;

    @Schema(
            description = "User role",
            example = "TRADING_AGENT",
            allowableValues = {"ADMIN", "TRADING_AGENT"})
    private final UserRole role;

    @Schema(description = "Profile creation time", example = "2026-08-23T10:15:30Z")
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
