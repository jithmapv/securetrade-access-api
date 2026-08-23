package com.securetrade.accessapi.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@Schema(description = "New trading agent account and profile")
public class CreateAgentRequest {

    @Schema(description = "Login username", example = "agent.one", maxLength = 50)
    @NotBlank(message = "Username is required")
    @Size(max = 50, message = "Username must be 50 characters or less")
    private String username;

    @Schema(description = "Login password", example = "StrongPassword123!", format = "password")
    @NotBlank(message = "Password is required")
    private String password;

    @Schema(description = "Unique agent code", example = "AGENT-001", maxLength = 50)
    @NotBlank(message = "Agent code is required")
    @Size(max = 50, message = "Agent code must be 50 characters or less")
    private String agentCode;

    @Schema(description = "Agent display name", example = "Momentum Agent", maxLength = 100)
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must be 100 characters or less")
    private String name;

    @Schema(description = "Trading strategy name", example = "MOMENTUM", maxLength = 50)
    @NotBlank(message = "Strategy type is required")
    @Size(max = 50, message = "Strategy type must be 50 characters or less")
    private String strategyType;

    @Schema(description = "Largest volume the agent may request", example = "2500000.00")
    @NotNull(message = "Max allowed volume is required")
    @Positive(message = "Max allowed volume must be positive")
    @Digits(
            integer = 13,
            fraction = 2,
            message = "Max allowed volume must fit 13 whole digits and 2 decimal digits")
    private BigDecimal maxAllowedVolume;

    public CreateAgentRequest() {
    }

    public CreateAgentRequest(
            String username,
            String password,
            String agentCode,
            String name,
            String strategyType,
            BigDecimal maxAllowedVolume) {

        this.username = username;
        this.password = password;
        this.agentCode = agentCode;
        this.name = name;
        this.strategyType = strategyType;
        this.maxAllowedVolume = maxAllowedVolume;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getAgentCode() {
        return agentCode;
    }

    public void setAgentCode(String agentCode) {
        this.agentCode = agentCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStrategyType() {
        return strategyType;
    }

    public void setStrategyType(String strategyType) {
        this.strategyType = strategyType;
    }

    public BigDecimal getMaxAllowedVolume() {
        return maxAllowedVolume;
    }

    public void setMaxAllowedVolume(BigDecimal maxAllowedVolume) {
        this.maxAllowedVolume = maxAllowedVolume;
    }
}
