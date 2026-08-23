package com.securetrade.accessapi.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

public class CreateAgentRequest {

    @NotBlank(message = "Username is required")
    private String username;

    @NotBlank(message = "Password is required")
    private String password;

    @NotBlank(message = "Agent code is required")
    private String agentCode;

    @NotBlank(message = "Name is required")
    private String name;

    @NotBlank(message = "Strategy type is required")
    private String strategyType;

    @NotNull(message = "Max allowed volume is required")
    @Positive(message = "Max allowed volume must be positive")
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
